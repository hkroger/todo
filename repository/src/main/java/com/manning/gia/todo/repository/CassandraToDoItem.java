package com.manning.gia.todo.repository;

import com.datastax.driver.mapping.annotations.ClusteringColumn;
import com.datastax.driver.mapping.annotations.Column;
import com.datastax.driver.mapping.annotations.PartitionKey;
import com.datastax.driver.mapping.annotations.Table;
import com.manning.gia.todo.model.ToDoItem;

@Table(keyspace = "todos", name = "todos",
        readConsistency = "QUORUM",
        writeConsistency = "QUORUM")
public class CassandraToDoItem {
    @PartitionKey
    @Column(name = "user_id")
    private String userId;

    @ClusteringColumn
    @Column(name = "id")
    private Long id;

    @Column(name = "name")
    private String name;

    @Column(name = "is_completed")
    private Boolean isCompleted;

    public CassandraToDoItem() {
    }

    public CassandraToDoItem(ToDoItem toDoItem) {
        userId = "dummy";
        id = toDoItem.getId();
        name = toDoItem.getName();
        isCompleted = toDoItem.isCompleted();
    }

    public ToDoItem toTodoItem() {
        ToDoItem i = new ToDoItem();
        i.setId(id);
        i.setName(name);
        i.setCompleted(isCompleted);
        return i;
    }
}
