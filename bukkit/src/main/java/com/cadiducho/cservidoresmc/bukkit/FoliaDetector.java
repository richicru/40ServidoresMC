package com.cadiducho.cservidoresmc.bukkit;

/**
 * Detección runtime de Folia vía {@code Class.forName}.
 *
 * <p>Mirar por la presencia de la clase {@code RegionizedServer} es la forma
 * recomendada por PaperMC para detectar Folia. Falsos positivos son raros y
 * funciona tanto en servidores viejos (no encuentra la clase → clásico) como
 * en nuevas builds Folia.</p>
 */
final class FoliaDetector {

    private static final String FOLIA_MARKER_CLASS = "io.papermc.paper.threadedregions.RegionizedServer";

    private static final boolean FOLIA = detectFolia();

    private FoliaDetector() {}

    /**
     * @return {@code true} si la JVM tiene Folia cargado en el classpath
     *         (es decir, el servidor es Folia).
     */
    static boolean isFoliaServer() {
        return FOLIA;
    }

    private static boolean detectFolia() {
        try {
            Class.forName(FOLIA_MARKER_CLASS);
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        } catch (LinkageError ignored) {
            // Ej.: si la clase está presente pero no se puede enlazar por ABI — tratamos como no-Folia.
            return false;
        }
    }
}
