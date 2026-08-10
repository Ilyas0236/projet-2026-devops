package com.wydad.digital.ticket.model;

import com.wydad.digital.ticket.enums.EventStatus;
import com.wydad.digital.ticket.enums.EventType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "events")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Event {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EventType eventType;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private EventStatus status = EventStatus.UPCOMING;

    private String homeTeam;
    private String awayTeam;
    private String venue;
    private String competition;

    @Column(nullable = false)
    private LocalDateTime eventDate;

    private LocalDateTime gateOpenTime;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal basePrice;

    private Integer totalCapacity;
    @Builder.Default
    private Integer availableSeats = 0;
    @Builder.Default
    private Integer soldTickets = 0;

    private String posterUrl;

    @OneToMany(mappedBy = "event", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Section> sections = new ArrayList<>();

    @OneToMany(mappedBy = "event", cascade = CascadeType.ALL)
    @Builder.Default
    private List<Ticket> tickets = new ArrayList<>();

    @CreationTimestamp private LocalDateTime createdAt;
    @UpdateTimestamp private LocalDateTime updatedAt;
}
