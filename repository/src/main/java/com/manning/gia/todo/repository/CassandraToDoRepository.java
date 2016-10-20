package com.manning.gia.todo.repository;
import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.Statement;
import com.datastax.driver.core.querybuilder.QueryBuilder;
import com.datastax.driver.mapping.Mapper;
import com.datastax.driver.mapping.MappingManager;
import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.ClientTracer;
import com.github.kristofa.brave.SpanId;
import com.manning.gia.todo.model.ToDoItem;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

public class CassandraToDoRepository implements ToDoRepository {
    private final Session session;
    private final Mapper<CassandraToDoItem> mapper;
    private Brave brave;

    public CassandraToDoRepository() {
        InetSocketAddress address = new InetSocketAddress("127.0.0.1", 9043);
        Cluster cluster = Cluster.builder().addContactPointsWithPorts(address).build();
        session = cluster.connect("todos");
        MappingManager manager = new MappingManager(session);
        mapper = manager.mapper(CassandraToDoItem.class);
    }

    @Override
    public List<ToDoItem> findAll() {
        Statement q = QueryBuilder.select().all().from("todos").where(QueryBuilder.eq("user_id", "dummy"));
        ResultSet results = executeInstrumented(q, "FIND ALL");
        return mapper.map(results).all().stream().map(CassandraToDoItem::toTodoItem).collect(Collectors.toList());
    }

    private ResultSet executeInstrumented(Statement q, String name) {
        ClientTracer clientTracer = getBrave().clientTracer();
        SpanId spanId = clientTracer.startNewSpan(name);
        ByteBuffer traceHeaders = ByteBuffer.wrap(spanId.bytes());

        q.setOutgoingPayload(Collections.singletonMap("zipkin", traceHeaders));
        clientTracer.setClientSent();
        ResultSet results = session.execute(q);
        clientTracer.setClientReceived();

        return results;
    }

    @Override
    public List<ToDoItem> findAllActive() {
        throw new NotImplementedYetException();
    }

    @Override
    public List<ToDoItem> findAllCompleted() {
        throw new NotImplementedYetException();
    }

    @Override
    public ToDoItem findById(Long id) {
        Statement q = mapper.getQuery("dummy", id);
        ResultSet results = executeInstrumented(q, "FIND BY ID");
        CassandraToDoItem item = mapper.map(results).one();

        if (item != null) {
            return item.toTodoItem();
        } else {
            return null;
        }
    }

    @Override
    public Long insert(ToDoItem toDoItem) {
        if (toDoItem.getId() == null) {
            toDoItem.setId(new Date().getTime());
        }
        save(toDoItem);
        return toDoItem.getId();
    }

    @Override
    public void update(ToDoItem toDoItem) {
        save(toDoItem);
    }

    private void save(ToDoItem toDoItem) {
        CassandraToDoItem cti = new CassandraToDoItem(toDoItem);
        Statement q = mapper.saveQuery(cti);
        executeInstrumented(q, "UPSERT TODO ITEM");
    }

    @Override
    public void delete(ToDoItem toDoItem) {
        CassandraToDoItem cti = new CassandraToDoItem(toDoItem);
        Statement q = mapper.deleteQuery(cti);
        executeInstrumented(q, "DELETE BY ID");
    }

    public Brave getBrave() {
        return brave;
    }

    public void setBrave(Brave brave) {
        this.brave = brave;
    }

    private class NotImplementedYetException extends RuntimeException {
    }
}

