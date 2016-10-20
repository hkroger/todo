package com.manning.gia.todo.web;

import com.github.kristofa.brave.*;
import com.github.kristofa.brave.http.*;
import com.github.kristofa.brave.servlet.ServletHttpServerRequest;
import com.manning.gia.todo.model.ToDoItem;
import com.manning.gia.todo.repository.CassandraToDoRepository;
import com.manning.gia.todo.repository.ToDoRepository;
import zipkin.reporter.AsyncReporter;
import zipkin.reporter.kafka08.KafkaSender;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ToDoServlet extends HttpServlet {
    public static final String FIND_ALL_SERVLET_PATH = "/all";
    public static final String INDEX_PAGE = "/jsp/todo-list.jsp";
    public static final String BRAVE_INSTANCE = "braveInstance";
    private ToDoRepository toDoRepository;

    @Override
    public void init() {
        Brave brave = initBrave();
        initRepository(brave);
    }

    private void initRepository(Brave brave) {
        CassandraToDoRepository repo = new CassandraToDoRepository();
        toDoRepository = repo;

        repo.setBrave(brave);
    }

    private Brave initBrave() {
        ServletContext ctx = getServletContext();
        String bootstrapServers = ctx.getInitParameter("ZipkinKafkaBootstrapServers");
        Brave brave = new Brave.Builder("Todo Application")
                .reporter(AsyncReporter.builder(KafkaSender.builder().bootstrapServers(bootstrapServers).build()).build())
                .build();

        ctx.setAttribute(BRAVE_INSTANCE, brave);
        return brave;
    }

    private Brave getBrave() {
        return (Brave) getServletContext().getAttribute(BRAVE_INSTANCE);
    }

    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        // Log request
        ServerRequestInterceptor requestInterceptor = getBrave().serverRequestInterceptor();
        ServerResponseInterceptor responseInterceptor = getBrave().serverResponseInterceptor();
        SpanNameProvider spanNameProvider = new DefaultSpanNameProvider();
        requestInterceptor.handle(new HttpServerRequestAdapter(new ServletHttpServerRequest((HttpServletRequest) request), spanNameProvider ));

        String servletPath = request.getServletPath();
        LocalTracer localTracer = getBrave().localTracer();
        localTracer.startNewSpan("Todo Service Process Request", servletPath);
        String view = processRequest(servletPath, request);
        localTracer.finishSpan();
        RequestDispatcher dispatcher = request.getRequestDispatcher(view);
        dispatcher.forward(request, response);

        // Log response
        responseInterceptor.handle(new HttpServerResponseAdapter(() -> response.getStatus()));
    }

    private String processRequest(String servletPath, HttpServletRequest request) {
        if (servletPath.equals(FIND_ALL_SERVLET_PATH)) {
            List<ToDoItem> toDoItems = toDoRepository.findAll();
            request.setAttribute("toDoItems", toDoItems);
            request.setAttribute("stats", determineStats(toDoItems));
            request.setAttribute("filter", "all");
            return INDEX_PAGE;
        } else if (servletPath.equals("/active")) {
            List<ToDoItem> toDoItems = toDoRepository.findAll();
            request.setAttribute("toDoItems", filterBasedOnStatus(toDoItems, true));
            request.setAttribute("stats", determineStats(toDoItems));
            request.setAttribute("filter", "active");
            return INDEX_PAGE;
        } else if (servletPath.equals("/completed")) {
            List<ToDoItem> toDoItems = toDoRepository.findAll();
            request.setAttribute("toDoItems", filterBasedOnStatus(toDoItems, false));
            request.setAttribute("stats", determineStats(toDoItems));
            request.setAttribute("filter", "completed");
            return INDEX_PAGE;
        } else if (servletPath.equals("/insert")) {
            ToDoItem toDoItem = new ToDoItem();
            toDoItem.setName(request.getParameter("name"));
            toDoRepository.insert(toDoItem);
            return "/" + request.getParameter("filter");
        } else if (servletPath.equals("/update")) {
            ToDoItem toDoItem = toDoRepository.findById(Long.parseLong(request.getParameter("id")));

            if (toDoItem != null) {
                toDoItem.setName(request.getParameter("name"));
                toDoRepository.update(toDoItem);
            }

            return "/" + request.getParameter("filter");
        } else if (servletPath.equals("/delete")) {
            ToDoItem toDoItem = toDoRepository.findById(Long.parseLong(request.getParameter("id")));

            if (toDoItem != null) {
                toDoRepository.delete(toDoItem);
            }

            return "/" + request.getParameter("filter");
        } else if (servletPath.equals("/toggleStatus")) {
            ToDoItem toDoItem = toDoRepository.findById(Long.parseLong(request.getParameter("id")));

            if (toDoItem != null) {
                boolean completed = "on".equals(request.getParameter("toggle")) ? true : false;
                toDoItem.setCompleted(completed);
                toDoRepository.update(toDoItem);
            }

            return "/" + request.getParameter("filter");
        } else if (servletPath.equals("/clearCompleted")) {
            List<ToDoItem> toDoItems = toDoRepository.findAll();

            for (ToDoItem toDoItem : toDoItems) {
                if (toDoItem.isCompleted()) {
                    toDoRepository.delete(toDoItem);
                }
            }

            return "/" + request.getParameter("filter");
        }

        return FIND_ALL_SERVLET_PATH;
    }

    private List<ToDoItem> filterBasedOnStatus(List<ToDoItem> toDoItems, boolean active) {
        List<ToDoItem> filteredToDoItems = new ArrayList<ToDoItem>();

        for (ToDoItem toDoItem : toDoItems) {
            if (toDoItem.isCompleted() != active) {
                filteredToDoItems.add(toDoItem);
            }
        }

        return filteredToDoItems;
    }

    private ToDoListStats determineStats(List<ToDoItem> toDoItems) {
        ToDoListStats toDoListStats = new ToDoListStats();

        for (ToDoItem toDoItem : toDoItems) {
            if (toDoItem.isCompleted()) {
                toDoListStats.addCompleted();
            } else {
                toDoListStats.addActive();
            }
        }

        return toDoListStats;
    }

    public class ToDoListStats {
        private int active;
        private int completed;

        private void addActive() {
            active++;
        }

        private void addCompleted() {
            completed++;
        }

        public int getActive() {
            return active;
        }

        public int getCompleted() {
            return completed;
        }

        public int getAll() {
            return active + completed;
        }
    }
}