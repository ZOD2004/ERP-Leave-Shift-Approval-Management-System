package com.murali.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "navigation_menu_roles", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"nav_item_id", "role_name"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NavMenuRole {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    //normalized acc to suggestion

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "nav_item_id", nullable = false)
    private NavMenuItem navMenuItem;

    @Column(name = "role_name", nullable = false)
    private String roleName;
}