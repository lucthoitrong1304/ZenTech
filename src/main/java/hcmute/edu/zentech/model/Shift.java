package hcmute.edu.zentech.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "shifts")
@NoArgsConstructor
@AllArgsConstructor
@Data
public class Shift {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String name;

    private LocalTime startTime;

    private LocalTime endTime;

    private String colorCode;

    private boolean isDefault;

    @Enumerated(EnumType.STRING)
    private ShiftType type;

    private Integer gracePeriodMinutes = 15;
}

