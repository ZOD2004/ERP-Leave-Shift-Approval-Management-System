package com.murali.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "navigation_menu")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class NavMenuItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String label;

    @Column(nullable = false)
    private String path;

    @Column(nullable = false)
    private String iconName;

    @Column(name = "role_name", nullable = false)
    private String roleName;

}
