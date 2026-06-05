package com.murali.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "navigation_items", uniqueConstraints = {@UniqueConstraint(columnNames = {"path"})})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NavMenuItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String path;

    @Column(nullable = false)
    private String label;

    @Column(nullable = false)
    private String iconName;
}