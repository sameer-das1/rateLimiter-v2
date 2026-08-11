package com.example.demo.controller;

import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class UserController {

    @GetMapping("/users")
    public List<String> getUsers() {

        return List.of(
                "Sameer",
                "Rahul",
                "Amit"
        );
    }
}