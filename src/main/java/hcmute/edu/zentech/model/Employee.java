package hcmute.edu.zentech.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "employees")
@NoArgsConstructor
@AllArgsConstructor
@Data
public class Employee {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String fullName;

    private String imageUrl;

    private String phoneNumber;

    private String address;

    private LocalDate dateOfBirth;

    @OneToOne
    @JoinColumn(name = "account_id", referencedColumnName = "account_id")
    private AccountUser userInfo;

    @Column(columnDefinition = "TEXT")
    private String faceDescriptors;
}
