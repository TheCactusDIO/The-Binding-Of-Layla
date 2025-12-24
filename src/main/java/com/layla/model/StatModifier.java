package com.layla.model;

import java.util.Objects;

/**
 * Modificador inmutable que afecta a una única estadística del jugador.
 * <p>
 * La estadística final se calcula así:
 * </p>
 * <pre>
 * (base + sumaAditiva) * productoMultiplicativo
 * </pre>
 * <p>
 * Este objeto representa una contribución a esa suma/producto.
 * </p>
 */
public final class StatModifier {

    private final PlayerStatId statId;
    private final double additive;
    private final double multiplicative;

    private StatModifier(PlayerStatId statId, double additive, double multiplicative) {
        this.statId = Objects.requireNonNull(statId, "statId");
        this.additive = additive;
        this.multiplicative = multiplicative;
    }

    /**
     * Crea un modificador aditivo (suma al valor base).
     *
     * @param statId stat a modificar
     * @param amount cantidad a sumar
     * @return modificador creado
     */
    public static StatModifier additive(PlayerStatId statId, double amount) {
        return new StatModifier(statId, amount, 1.0);
    }

    /**
     * Crea un modificador multiplicativo (multiplica el resultado de (base + aditivo)).
     *
     * @param statId stat a modificar
     * @param factor factor multiplicativo
     * @return modificador creado
     */
    public static StatModifier multiplicative(PlayerStatId statId, double factor) {
        return new StatModifier(statId, 0.0, factor);
    }

    /**
     * Fábrica para un modificador combinado (aditivo y multiplicativo).
     *
     * @param statId stat a modificar
     * @param additive cantidad aditiva
     * @param multiplicative factor multiplicativo
     * @return modificador creado
     */
    public static StatModifier of(PlayerStatId statId, double additive, double multiplicative) {
        return new StatModifier(statId, additive, multiplicative);
    }

    /**
     * @return identificador del stat modificado.
     */
    public PlayerStatId getStatId() {
        return statId;
    }

    /**
     * @return componente aditiva del modificador.
     */
    public double getAdditive() {
        return additive;
    }

    /**
     * @return componente multiplicativa del modificador.
     */
    public double getMultiplicative() {
        return multiplicative;
    }
}
