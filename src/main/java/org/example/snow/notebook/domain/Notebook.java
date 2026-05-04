package org.example.snow.notebook.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.snow.user.domain.UserAccount;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "notebook")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notebook {

    public static final String DEFAULT_TITLE = "새 노트북";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notebook_id")
    private Long notebookId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_notebook_user"))
    private UserAccount user;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    private Notebook(UserAccount user, String title, boolean isDefault) {
        this.user = user;
        this.title = title;
        this.isDefault = isDefault;
    }

    public static Notebook createDefault(UserAccount user) {
        return new Notebook(user, DEFAULT_TITLE, true);
    }

    public static Notebook create(UserAccount user, String title) {
        return new Notebook(user, title, false);
    }

    public void rename(String title) {
        this.title = title;
    }

    public void softDelete() {
        this.deletedAt = LocalDateTime.now();
    }

    public boolean isDeleted() {
        return this.deletedAt != null;
    }

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
