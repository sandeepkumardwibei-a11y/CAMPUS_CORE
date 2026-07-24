package com.campuscore.AspectforLog;

import com.campuscore.entity.ModuleLog;
import com.campuscore.entity.User;
import com.campuscore.repository.ModuleLogRepository;
import com.campuscore.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class ModuleLoggingAspect {

    private final ModuleLogRepository moduleLogRepository;
    private final UserRepository userRepository;

    // 🎯 Target all execution methods inside any class within the controller package
    @Pointcut("execution(* com.campuscore.controller.*.*(..))")
    public void controllerMethods() {}

    // Run this automatically after any controller method successfully executes/returns data
    @AfterReturning("controllerMethods()")
    public void logModuleAccess(JoinPoint joinPoint) {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            
            // Skip logging if the request is anonymous (e.g., before logging in)
            if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getName())) {
                return;
            }

            String currentEmail = authentication.getName();
            Optional<User> userOpt = userRepository.findByEmail(currentEmail);

            if (userOpt.isPresent()) {
                User user = userOpt.get();
                
                // 1. Deduce Module Name cleanly from Controller Class Name (e.g., "TimetableController" -> "TIMETABLE")
                String className = joinPoint.getTarget().getClass().getSimpleName();
                String moduleName = className.replace("Controller", "").toUpperCase();

                // 2. Identify the action performed from the method name (e.g., "getStudentSchedule")
                String actionPerformed = joinPoint.getSignature().getName();

                ModuleLog logEntry = ModuleLog.builder()
                        .moduleName(moduleName)
                        .actionPerformed(actionPerformed)
                        .user(user)
                        .accessedBy(user.getName()) // 🎯 Captures their exact profile name
                        .build();

                moduleLogRepository.save(logEntry);
                log.debug("Auto-Audited module access: User '{}' accessed module '{}' -> action '{}'", user.getName(), moduleName, actionPerformed);
            }
        } catch (Exception e) {
            // Prevent log saving issues from breaking the actual application user experience
            log.error("Failed to write to module log table seamlessly", e);
        }
    }
}