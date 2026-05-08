package org.example.snow.ai.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "embeddings")
public class Embedding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(columnDefinition = "TEXT")
    private String text;

    @Column(columnDefinition = "vector(1024)")
    private float[] embedding;

    public Embedding() {}

    public Embedding(String text, float[] embedding) {
        this.text = text;
        this.embedding = embedding;
    }

    public Long getId() {
        return id;
    }

    public String getText() {
        return text;
    }

    public float[] getEmbedding() {
        return embedding;
    }

    public void setText(String text) {
        this.text = text;
    }

    public void setEmbedding(float[] embedding) {
        this.embedding = embedding;
    }
}