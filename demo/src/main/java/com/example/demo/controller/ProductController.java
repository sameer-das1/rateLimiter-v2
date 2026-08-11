package com.example.demo.controller;

import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class ProductController {

    @GetMapping("/products")
    public List<String> getProducts() {

        return List.of(
                "Laptop",
                "Phone",
                "Keyboard",
                "Mouse"
        );
    }
}