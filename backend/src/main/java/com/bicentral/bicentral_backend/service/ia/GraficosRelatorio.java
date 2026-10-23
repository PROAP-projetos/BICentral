package com.bicentral.bicentral_backend.service.ia;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.CategoryAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.labels.StandardCategoryItemLabelGenerator;
import org.jfree.chart.labels.StandardPieSectionLabelGenerator;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.RingPlot;
import org.jfree.chart.plot.ValueMarker;
import org.jfree.chart.renderer.category.BarRenderer;
import org.jfree.chart.renderer.category.StandardBarPainter;
import org.jfree.chart.title.TextTitle;
import org.jfree.chart.ui.RectangleInsets;
import org.jfree.chart.ui.TextAnchor;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.data.general.DefaultPieDataset;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.RenderingHints;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

/**
 * Gráficos dos relatórios (PDF/DOCX) desenhados no próprio backend como PNG — o painel web usa
 * ECharts, mas ECharts precisa de navegador/JS. As cores são as mesmas do resto do documento
 * (barra de distribuição, tabelas), pra o gráfico não destoar da página. O Excel não leva
 * gráfico: a planilha é pra trabalhar os dados, não pra leitura.
 */
final class GraficosRelatorio {

    static final Color VERDE = Color.decode("#2E7D32");
    static final Color AZUL = Color.decode("#2E6F9E");
    static final Color VERMELHO = Color.decode("#C00000");
    static final Color CINZA = Color.decode("#8A8A94");
    private static final Color NAVY = Color.decode("#1F4E79");
    private static final Color TEXTO = Color.decode("#334155");
    private static final Color GRADE = Color.decode("#e2e8f0");

    // Renderiza em 2x e o documento reduz pro tamanho em pontos: sem isso o PNG fica serrilhado no PDF impresso.
    private static final int ESCALA = 2;

    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

    private GraficosRelatorio() {
    }

    record Fatia(String rotulo, int valor, Color cor) {
    }

    record Barra(String rotulo, double valor) {
    }

    /** Rosca de distribuição (ex: concluídas / em execução / sem execução). Fatias zeradas ficam de fora pra não poluir os rótulos. */
    static byte[] rosca(String titulo, List<Fatia> fatias, int largura, int altura) {
        DefaultPieDataset<String> dados = new DefaultPieDataset<>();
        for (Fatia f : fatias) {
            if (f.valor() > 0) dados.setValue(f.rotulo(), f.valor());
        }

        RingPlot plot = new RingPlot(dados);
        for (Fatia f : fatias) {
            if (f.valor() > 0) plot.setSectionPaint(f.rotulo(), f.cor());
        }
        plot.setSectionDepth(0.38);
        plot.setSeparatorsVisible(false);
        plot.setShadowPaint(null);
        plot.setOutlineVisible(false);
        plot.setBackgroundPaint(Color.WHITE);
        plot.setLabelFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        plot.setLabelPaint(TEXTO);
        plot.setLabelBackgroundPaint(Color.WHITE);
        plot.setLabelOutlinePaint(null);
        plot.setLabelShadowPaint(null);
        NumberFormat inteiro = NumberFormat.getIntegerInstance(PT_BR);
        NumberFormat percentual = NumberFormat.getPercentInstance(PT_BR);
        plot.setLabelGenerator(new StandardPieSectionLabelGenerator("{0}: {1} ({2})", inteiro, percentual));

        JFreeChart chart = new JFreeChart(null, JFreeChart.DEFAULT_TITLE_FONT, plot, false);
        aplicarTitulo(chart, titulo);
        return png(chart, largura, altura);
    }

    /**
     * Barras horizontais de 0 a 100%, com linha de referência opcional (esperado para a época do ano).
     * Abaixo da referência a barra fica vermelha, na referência ou acima fica verde; sem referência, azul.
     * A referência vira linha tracejada explicada num subtítulo — rótulo em cima da linha batia no rótulo da barra.
     */
    static byte[] barrasHorizontais(String titulo, List<Barra> barras, Integer referencia, String rotuloReferencia, int largura, int altura) {
        DefaultCategoryDataset dados = new DefaultCategoryDataset();
        for (Barra b : barras) {
            dados.addValue(b.valor(), "Execução", b.rotulo());
        }

        JFreeChart chart = ChartFactory.createBarChart(null, null, null, dados, PlotOrientation.HORIZONTAL, false, false, false);
        aplicarTitulo(chart, titulo);

        CategoryPlot plot = chart.getCategoryPlot();
        plot.setBackgroundPaint(Color.WHITE);
        plot.setOutlineVisible(false);
        plot.setRangeGridlinePaint(GRADE);
        plot.setRangeGridlineStroke(new BasicStroke(1f));
        plot.setInsets(new RectangleInsets(4, 4, 4, 14));

        NumberAxis eixoValor = (NumberAxis) plot.getRangeAxis();
        // Folga depois de 100% pra o rótulo "100%" à direita da barra não ser cortado; os ticks param em 100.
        eixoValor.setRange(0, 112);
        eixoValor.setTickUnit(new org.jfree.chart.axis.NumberTickUnit(20));
        eixoValor.setNumberFormatOverride(new DecimalFormat("0'%'"));
        eixoValor.setTickLabelFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
        eixoValor.setTickLabelPaint(TEXTO);
        eixoValor.setAxisLineVisible(false);

        CategoryAxis eixoCategoria = plot.getDomainAxis();
        eixoCategoria.setTickLabelFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
        eixoCategoria.setTickLabelPaint(TEXTO);
        eixoCategoria.setAxisLineVisible(false);
        eixoCategoria.setMaximumCategoryLabelWidthRatio(0.35f);
        eixoCategoria.setCategoryMargin(0.25);

        BarRenderer renderer = new BarRenderer() {
            @Override
            public Paint getItemPaint(int linha, int coluna) {
                if (referencia == null) return AZUL;
                Number valor = dados.getValue(linha, coluna);
                return valor != null && valor.doubleValue() >= referencia ? VERDE : VERMELHO;
            }
        };
        renderer.setBarPainter(new StandardBarPainter());
        renderer.setShadowVisible(false);
        renderer.setMaximumBarWidth(barras.size() == 1 ? 0.4 : 0.12);
        renderer.setDefaultItemLabelGenerator(new StandardCategoryItemLabelGenerator("{2}", formatoPercentual()));
        renderer.setDefaultItemLabelsVisible(true);
        renderer.setDefaultItemLabelFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
        renderer.setDefaultItemLabelPaint(TEXTO);
        renderer.setDefaultPositiveItemLabelPosition(new org.jfree.chart.labels.ItemLabelPosition(
                org.jfree.chart.labels.ItemLabelAnchor.OUTSIDE3, TextAnchor.CENTER_LEFT));
        renderer.setItemLabelAnchorOffset(4);
        plot.setRenderer(renderer);

        if (referencia != null) {
            plot.addRangeMarker(new ValueMarker(referencia, NAVY, new BasicStroke(1.6f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{5f, 4f}, 0f)));
            TextTitle legenda = new TextTitle("Linha tracejada: " + (rotuloReferencia != null ? rotuloReferencia.toLowerCase(PT_BR) + ", " : "esperado, ")
                    + referencia + "%", new Font(Font.SANS_SERIF, Font.PLAIN, 10));
            legenda.setPaint(TEXTO);
            chart.addSubtitle(legenda);
        }

        return png(chart, largura, altura);
    }

    private static NumberFormat formatoPercentual() {
        return new DecimalFormat("0.#'%'", DecimalFormatSymbols.getInstance(PT_BR));
    }

    private static void aplicarTitulo(JFreeChart chart, String titulo) {
        chart.setBackgroundPaint(Color.WHITE);
        chart.setBorderVisible(false);
        if (titulo != null) {
            TextTitle t = new TextTitle(titulo, new Font(Font.SANS_SERIF, Font.BOLD, 13));
            t.setPaint(NAVY);
            t.setPadding(new RectangleInsets(2, 0, 6, 0));
            chart.setTitle(t);
        }
    }

    private static byte[] png(JFreeChart chart, int largura, int altura) {
        BufferedImage imagem = new BufferedImage(largura * ESCALA, altura * ESCALA, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = imagem.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.scale(ESCALA, ESCALA);
            chart.draw(g, new Rectangle2D.Double(0, 0, largura, altura));
        } finally {
            g.dispose();
        }
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(imagem, "png", out);
            return out.toByteArray();
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Falha ao gerar imagem do gráfico", e);
        }
    }
}
