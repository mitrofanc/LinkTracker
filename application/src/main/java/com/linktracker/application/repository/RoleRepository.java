package com.linktracker.application.repository;

import com.linktracker.application.model.Role;
import com.linktracker.application.model.RoleName;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByName(RoleName name);
}
