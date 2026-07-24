package com.campuscore.config;
 
import static java.lang.Math.log;
 
import com.campuscore.entity.Course;
import com.campuscore.entity.HostelRoom;
import com.campuscore.entity.Program;
import com.campuscore.entity.User;
import com.campuscore.repository.CourseRepository;
import com.campuscore.repository.HostelRoomRepository;
import com.campuscore.repository.ProgramRepository;
import com.campuscore.repository.UserRepository;
 
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
 
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
 
import java.util.List;
 
// @Component
@RequiredArgsConstructor
@Slf4j
public class DatabaseSeeder implements CommandLineRunner {
 
    private final UserRepository userRepository;
    private final ProgramRepository programRepository;
    private final CourseRepository courseRepository;
    private final HostelRoomRepository roomRepository;
    private final PasswordEncoder passwordEncoder;
 
    @Override
    public void run(String... args) throws Exception {
        log.info("Checking database for seed data...");
 
        // 1. Seed Programs
        if (programRepository.count() == 0) {
            log.info("Seeding Programs...");
            Program cs = Program.builder()
                    .programName("B.Tech Computer Science")
                    .level(Program.Level.UG)
                    .durationYears(4)
                    .totalSeats(120)
                    .status(Program.ProgramStatus.ACTIVE)
                    .build();
 
            Program ds = Program.builder()
                    .programName("M.Tech Data Science")
                    .level(Program.Level.PG)
                    .durationYears(2)
                    .totalSeats(60)
                    .status(Program.ProgramStatus.ACTIVE)
                    .build();
 
            programRepository.saveAll(List.of(cs, ds));
        }
 
        // 2. Seed Users
        if (userRepository.count() == 0) {
            log.info("Seeding Users...");
            User admin = User.builder()
                    .name("System Admin")
                    .email("admin@campuscore.com")
                    .password(passwordEncoder.encode("admin123"))
                    .phone("1234567890")
                    .role(User.Role.ADMIN)
                    .status(User.UserStatus.ACTIVE)
                    .build();
 
            User exam = User.builder()
                    .name("Exam Controller")
                    .email("exam@campuscore.com")
                    .password(passwordEncoder.encode("exam123"))
                    .phone("1234567891")
                    .role(User.Role.EXAM_CONTROLLER)
                    .status(User.UserStatus.ACTIVE)
                    .build();
 
            User accounts = User.builder()
                    .name("Accounts Head")
                    .email("accounts@campuscore.com")
                    .password(passwordEncoder.encode("accounts123"))
                    .phone("1234567892")
                    .role(User.Role.ACCOUNTS)
                    .status(User.UserStatus.ACTIVE)
                    .build();
 
            User hostelAdmin = User.builder()
                    .name("Hostel Admin")
                    .email("hostel@campuscore.com")
                    .password(passwordEncoder.encode("hostel123"))
                    .phone("1234567895")
                    .role(User.Role.HOSTEL_ADMIN)
                    .status(User.UserStatus.ACTIVE)
                    .build();
 
            User faculty1 = User.builder()
                    .name("Dr. Alan Turing")
                    .email("turing@campuscore.com")
                    .password(passwordEncoder.encode("faculty123"))
                    .phone("1234567893")
                    .role(User.Role.FACULTY)
                    .departmentId(1L)
                    .status(User.UserStatus.ACTIVE)
                    .build();
 
            User student1 = User.builder()
                    .name("Khushal Kumar")
                    .email("student1@campuscore.com")
                    .password(passwordEncoder.encode("student123"))
                    .phone("1234567894")
                    .role(User.Role.STUDENT)
                    .status(User.UserStatus.ACTIVE)
                    .build();
 
            userRepository.saveAll(List.of(admin, exam, accounts, faculty1, student1, hostelAdmin));
        }
 
        // 3. Seed Courses
        if (courseRepository.count() == 0) {
            log.info("Seeding Courses...");
            Program program = programRepository.findAll().stream()
                    .filter(p -> p.getProgramName().contains("Computer Science"))
                    .findFirst().orElse(null);
 
            User faculty = userRepository.findByEmail("turing@campuscore.com").orElse(null);
 
            if (program != null && faculty != null) {
                Course dsa = Course.builder()
                        .courseName("Data Structures & Algorithms")
                        .courseCode("CS201")
                        .program(program)
                        .semester(3)
                        .credits(4)
                        .faculty(faculty)
                        .maxEnrollment(60)
                        .status(Course.CourseStatus.ACTIVE)
                        .build();
 
                Course dbms = Course.builder()
                        .courseName("Database Management Systems")
                        .courseCode("CS202")
                        .program(program)
                        .semester(3)
                        .credits(4)
                        .faculty(faculty)
                        .maxEnrollment(60)
                        .status(Course.CourseStatus.ACTIVE)
                        .build();
 
                Course os = Course.builder()
                        .courseName("Operating Systems")
                        .courseCode("CS203")
                        .program(program)
                        .semester(3)
                        .credits(4)
                        .faculty(faculty)
                        .maxEnrollment(60)
                        .status(Course.CourseStatus.ACTIVE)
                        .build();
 
                courseRepository.saveAll(List.of(dsa, dbms, os));
            }
        }
 
        // 4. Seed Hostel Rooms
        if (roomRepository.count() == 0) {
            log.info("Seeding Hostel Rooms...");
            HostelRoom r1 = HostelRoom.builder()
                    .hostelBlock("A-Block")
                    .roomNumber("101")
                    .capacity(2)
                    .occupiedCount(0)
                    .roomType(HostelRoom.RoomType.DOUBLE)
                    .status(HostelRoom.RoomStatus.AVAILABLE)
                    .build();
 
            HostelRoom r2 = HostelRoom.builder()
                    .hostelBlock("A-Block")
                    .roomNumber("102")
                    .capacity(2)
                    .occupiedCount(0)
                    .roomType(HostelRoom.RoomType.DOUBLE)
                    .status(HostelRoom.RoomStatus.AVAILABLE)
                    .build();
 
            HostelRoom r3 = HostelRoom.builder()
                    .hostelBlock("B-Block")
                    .roomNumber("201")
                    .capacity(1)
                    .occupiedCount(0)
                    .roomType(HostelRoom.RoomType.SINGLE)
                    .status(HostelRoom.RoomStatus.AVAILABLE)
                    .build();
 
            roomRepository.saveAll(List.of(r1, r2, r3));
        }
 
        log.info("Database seeding complete.");
    }
}
 
 