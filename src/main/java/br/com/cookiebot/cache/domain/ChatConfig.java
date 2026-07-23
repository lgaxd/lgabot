package br.com.cookiebot.cache.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "chat_configs")
public class ChatConfig {

    @Id
    @Column(name = "chat_id")
    private Long chatId;

    @Column(name = "welcome_enabled")
    private boolean welcomeEnabled;

    @Column(name = "anti_raid_enabled")
    private boolean antiRaidEnabled;

    @Column(name = "raid_sensitivity")
    private int raidSensitivity;

    // Mapeamento nativo para colunas JSONB no PostgreSQL via Hibernate moderno
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "custom_rules_matrix", columnDefinition = "jsonb")
    private RulesMatrix customRulesMatrix;

    public ChatConfig() {}

    public Long getChatId() { return chatId; }
    public void setChatId(Long chatId) { this.chatId = chatId; }

    public boolean isWelcomeEnabled() { return welcomeEnabled; }
    public void setWelcomeEnabled(boolean welcomeEnabled) { this.welcomeEnabled = welcomeEnabled; }

    public boolean isAntiRaidEnabled() { return antiRaidEnabled; }
    public void setAntiRaidEnabled(boolean antiRaidEnabled) { this.antiRaidEnabled = antiRaidEnabled; }

    public int getRaidSensitivity() { return raidSensitivity; }
    public void setRaidSensitivity(int raidSensitivity) { this.raidSensitivity = raidSensitivity; }

    public RulesMatrix getCustomRulesMatrix() { return customRulesMatrix; }
    public void setCustomRulesMatrix(RulesMatrix customRulesMatrix) { this.customRulesMatrix = customRulesMatrix; }
}