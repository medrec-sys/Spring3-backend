package fun.medrec.spring.utils;

import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.springframework.ai.embedding.EmbeddingModel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class EmbeddingUtil {
    private static EmbeddingModel embeddingModel;
    private static final int MAX_LENGTH = 2000;
    private static final double SIMILARITY_THRESHOLD = 0.6;


    private EmbeddingUtil() {
        throw new AssertionError();
    }

    // 创建线程转换成向量
    private static List<float[]> embed(PdfUtil.PdfData pdfData) {
        List<String> pageContents = pdfData.getTexts();

        // 合并
        float[][] embeddingsArray = new float[pageContents.size()][];

        int len = pageContents.size();
        int batch = 10;
        int last = len % batch;
        int n = len / batch + (last == 0 ? 0 : 1);

        ExecutorService executor = Executors.newFixedThreadPool(15);
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (int i = 0; i < n; i++) {
            int start = i * batch;
            int end = (last != 0 && i == n - 1) ? i * batch + last : (i + 1) * batch;
            List<String> batchList = pageContents.subList(start, end);

            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                List<float[]> embed = embeddingModel.embed(batchList);
                for (int j = 0; j < embed.size(); j++) {
                    embeddingsArray[start + j] = embed.get(j);
                }
            }, executor);

            futures.add(future);
        }


        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        executor.shutdown();

        return Arrays.asList(embeddingsArray);
    }

    // 求相似度数组
    private static List<Boolean> getSimilarityArray(List<float[]> embeddings) {
        List<Boolean> cosIfCombine = new ArrayList<>();

        for (int i = 0; i < embeddings.size() - 1; i++) {
            float[] embedding = embeddings.get(i);
            float[] embedding2 = embeddings.get(i + 1);
            double cos = cos(embedding, embedding2);
            cosIfCombine.add(cos >= SIMILARITY_THRESHOLD);
        }

        return cosIfCombine;
    }

    // 句子合并
    private static PdfUtil.PdfData merge(PdfUtil.PdfData pdfData, List<Boolean> cosIfCombine) {
        List<String> pageContents = pdfData.getTexts();
        List<Integer> pageNumbers = pdfData.getIndexes();

        List<String> sentences = new ArrayList<>();
        StringBuilder sentence = new StringBuilder(pageContents.getFirst());
        Integer pageNum = pageNumbers.getFirst();
        List<Integer> pageNums = new ArrayList<>();
        for (int i = 0; i < cosIfCombine.size() - 1; i++) {
            if (cosIfCombine.get(i)  && sentence.length() + pageContents.get(i + 1).length() < MAX_LENGTH) {
                sentence.append(pageContents.get(i + 1));
            } else {
                sentences.add(sentence.toString());
                pageNums.add(pageNum);
                pageNum = pageNumbers.get(i + 1);
                sentence = new StringBuilder(pageContents.get(i + 1));
            }
        }

        sentences.add(sentence.toString());
        pageNums.add(pageNum);

        return new PdfUtil.PdfData(pdfData.getPdfName(), sentences, pageNums);
    }

    // 根据语义合并句子
    public static PdfUtil.PdfData mergeSentence(PdfUtil.PdfData pdfData, EmbeddingModel em) {
        embeddingModel = em;

        List<float[]> embeddings = embed(pdfData);

        List<Boolean> cosIfCombine = getSimilarityArray(embeddings);

        return merge(pdfData, cosIfCombine);
    }

    static double cos(float[] a, float[] b) {
        INDArray vec1 = Nd4j.create(a);
        INDArray vec2 = Nd4j.create(b);

        INDArray vec2Column = vec2.reshape(vec2.length(), 1);
        double dot = vec1.mmul(vec2Column).getDouble(0);

        double norm1 = vec1.norm2Number().doubleValue();
        double norm2 = vec2.norm2Number().doubleValue();

        return (norm1 * norm2 == 0) ? 0 : (dot / (norm1 * norm2));
    }
}
