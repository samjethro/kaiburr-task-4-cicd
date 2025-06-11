package com.kaiburr.taskmanager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Date;
import java.util.List;
import java.util.Optional;

@CrossOrigin(origins = "http://localhost:3000")
@RestController
@RequestMapping("/tasks")
public class TaskController {

    private static final Logger logger = LoggerFactory.getLogger(TaskController.class);
    private final TaskRepository taskRepository;

    public TaskController(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    @GetMapping("/ping")
    public String ping() {
        return "pong";
    }

    @GetMapping
    public List<Task> getAllTasks(@RequestParam(required = false) String id) {
        if (id != null) {
            Optional<Task> task = taskRepository.findById(id);
            return task.map(List::of).orElse(List.of());
        }
        return taskRepository.findAll();
    }

    // Command Validation
    @PutMapping
    public ResponseEntity<?> putTask(@RequestBody Task task) {
        String cmd = task.getCommand().toLowerCase();

        // Block dangerous commands
        String[] blackList = {"rm", "sudo", "shutdown", "reboot", "init", "halt",
                "poweroff", "mkfs", "dd", "kill", "passwd", "chown",
                "chmod", "del", "format"};

        for (String bad : blackList) {
            if (cmd.contains(bad)) {
                return ResponseEntity.badRequest()
                        .body("Invalid command: contains forbidden word '" + bad + "'");
            }
        }

        Task saved = taskRepository.save(task);
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping
    public String deleteTask(@RequestParam String id) {
        taskRepository.deleteById(id);
        return "Task Deleted Successfully!";
    }

    @GetMapping("/search")
    public List<Task> searchTasks(@RequestParam String name) {
        return taskRepository.findByNameContainingIgnoreCase(name);
    }

    @PutMapping("/{id}/execute")
    public ResponseEntity<?> executeCommand(@PathVariable String id) {
        Optional<Task> taskOptional = taskRepository.findById(id);
        if (taskOptional.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Task with id " + id + " not found");
        }

        Task task = taskOptional.get();

        try {
            Date start = new Date();
            Process process = Runtime.getRuntime().exec(new String[]{"cmd.exe", "/c", task.getCommand()});
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()));
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
            process.waitFor();
            Date end = new Date();

            TaskExecution execution = new TaskExecution(start, end, output.toString());
            task.getTaskExecutions().add(execution);
            Task savedTask = taskRepository.save(task);

            return ResponseEntity.ok(savedTask);

        } catch (Exception e) {
            logger.error("Error executing command for task ID {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error executing command: " + e.getMessage());
        }
    }
}

