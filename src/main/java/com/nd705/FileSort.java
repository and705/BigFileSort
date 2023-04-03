package com.nd705;

import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Сортировка больших файлов
 *
 * @author Andreev Andrei
 * @version 1.0
 *
 */
public class FileSort {
    public static void main(String[] args) throws IOException {

        int lines = 0;
        int maxLineLength = 0;
        String fileName = "text_file.txt";
        int FRAGMENTSIZE = 1024 * 1024 * 100; // 100 Mb


        //ввод данных
        Scanner sc = new Scanner(System.in);
        lines = getPosIntInput(sc, "Введите число строк: ");
        maxLineLength = getPosIntInput(sc, "Введите максимальное число символов в строке: ");


        //генерация файла
        String alphabet = createAlphabet();
        generateFile(fileName, lines, maxLineLength, alphabet);

        //Разбивка на сортированные фрагменты, объединение в отсортированый файл
        try {
            List<File> files = createSortedFragments(
                    fileName,
                    FRAGMENTSIZE,
                    new StringComparator());
            mergeSortedFragments(
                    files,
                    "sorted_" + fileName,
                    new StringComparator());
        } catch (IOException e) {
            System.out.println("Ошибка, сортировка не выполнена");
        }


    }

    /**
     * Создание отсортированных фрагментов
     *
     * @param fileName
     * @param maxFragmentSize
     * @return List<File>
     * @throws IOException
     *
     */
    public static List<File> createSortedFragments(String fileName,
                                                   int maxFragmentSize,
                                                   StringComparator comparator) throws IOException {
        String tempDirectory = "temp";
        List<File> files = new ArrayList<>();
        RandomAccessFile randomAccessFile = new RandomAccessFile(fileName, "r");
        long fileSize = randomAccessFile.length();

        long numOfFragments =  1 + fileSize / maxFragmentSize;
        long bytesPerFragment = fileSize / numOfFragments;
        if (fileSize % numOfFragments > 0) {
            numOfFragments += 1;
        }
        int maxReadBufferSize = 8 * 1024 ; // 8KB
        int fileCounter = 1;

        for (int i = 1; i <= numOfFragments; i++) {
            File dir = new File(tempDirectory);
            if (dir.exists()) {
                dir.delete();
            }
            dir.mkdir();
            File file = new File(tempDirectory + "/fragment-" + fileCounter + ".txt");
            BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(new FileOutputStream(file));

            if (bytesPerFragment > maxReadBufferSize) {
                long numReads = bytesPerFragment / maxReadBufferSize;
                long numRemainingRead = bytesPerFragment % maxReadBufferSize;
                for (int j = 0; j < numReads; j++) {
                    readWrite(randomAccessFile, bufferedOutputStream, maxReadBufferSize);
                }
                if (numRemainingRead > 0) {
                    readWrite(randomAccessFile, bufferedOutputStream, numRemainingRead);
                }
            } else {
                readWrite(randomAccessFile, bufferedOutputStream, bytesPerFragment);
            }
            file = sortFileContent(file, comparator);
            files.add(file);
            fileCounter++;
            bufferedOutputStream.close();
        }
        return files;
    }


    /**
     * Сохранение фрагмента во временный файл
     *
     * @param randomAccessFile
     * @param bufferedOutputStream
     * @param numBytes
     * @throws IOException
     *
     */
    private static void readWrite(RandomAccessFile randomAccessFile, BufferedOutputStream bufferedOutputStream, long numBytes) throws IOException {
        byte[] buf = new byte[(int) numBytes];
        int val = randomAccessFile.read(buf);
        int endLine = 0;

        for (int i = buf.length-1; i > 0 ; i--) {
            if (buf[i]=='\n'){
                endLine = i;
                break;
            }
        }

        randomAccessFile.seek(randomAccessFile.getFilePointer()-(buf.length-endLine)+1);

        if (val != -1) {
            bufferedOutputStream.write(buf, 0, endLine);
            bufferedOutputStream.flush();
        }

    }

    /**
     * Построчная сортировка содержимого файла
     *
     * @param file
     * @return file
     * @throws IOException
     *
     */
    private static File sortFileContent(File file, StringComparator cmp) throws IOException {
        List<String> lines = new ArrayList<>();
        try (Stream<String> ln = Files.lines(file.toPath())) {
            lines = ln.collect(Collectors.toList());
        }
        Collections.sort(lines, cmp);
        try (BufferedWriter bw = Files.newBufferedWriter(file.toPath())) {
            for (String line : lines) {
                bw.write(line);
                bw.write("\r\n");
            }
        }
        return file;
    }

    /**
     * Сборка фрагментов в единый файл
     *
     * @param files
     * @param resultFile
     * @throws IOException
     *
     */
    public static void mergeSortedFragments(List<File> files, String resultFile, StringComparator comparator)
            throws IOException {
        List<BufferedReader> brReaders = new ArrayList<>();
        TreeMap<String, BufferedReader> map = new TreeMap<>(comparator);
        File file = new File(resultFile);
        if (file.exists()) {
            file.delete();
        }
        BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter(resultFile, true));
        try {
            for (File f : files) {
                BufferedReader br = new BufferedReader(new FileReader(f));
                brReaders.add(br);
                String line = br.readLine();
                map.put(line, br);
            }
            while (!map.isEmpty()) {
                Map.Entry<String, BufferedReader> nextToGo = map.pollFirstEntry();
                bufferedWriter.write(nextToGo.getKey());
                bufferedWriter.write("\r\n");
                String line = nextToGo.getValue().readLine();
                if (line != null) {
                    map.put(line, nextToGo.getValue());
                }

            }
            System.out.println("Чтение и сортировка выполнены успешно\n" +
                    "Создан файл: " + resultFile + " по адресу: \n" +
                    file.getAbsolutePath());
        } finally {
            if (brReaders != null) {
                for (BufferedReader br : brReaders) {
                    br.close();
                }
                File dir = files.get(0).getParentFile();
                for (File f : files) {
                    f.delete();
                }
                if (dir.exists()) {
                    dir.delete();
                }
            }
            if (bufferedWriter != null) {
                bufferedWriter.close();
            }
        }
    }

    public static class StringComparator implements Comparator<String> {
        @Override
        public int compare(String s1, String s2) {
            return s1.compareToIgnoreCase(s2);
        }
    }

    /**
     * Генерация исходного файла
     *
     * @param fileName
     * @param lines
     * @param maxLineLength
     *
     */
    private static void generateFile(String fileName, int lines, int maxLineLength, String alphabet) {
        int minLineLength = 0;
        StringBuilder line = new StringBuilder();
        try (FileWriter fileWriter = new FileWriter(fileName)) {
            for (int i = 0; i < lines; i++) {
                int lineLength = (int) ((Math.random() * (maxLineLength - minLineLength)) + minLineLength);
                for (int j = 0; j <= lineLength; j++) {
                    line.append(alphabet.charAt((int) (Math.random() * alphabet.length())));
                }
                line.append(System.lineSeparator());
                fileWriter.write(line.toString());
                line.setLength(0);
            }
            System.out.println("Файл: " + fileName + " создан и заполнен");
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    /**
     * Генерация алфавита для файла
     *
     * @return String
     *
     */
    private static String createAlphabet() {
        StringBuilder alphabet = new StringBuilder();
        for (int i = 'a'; i <= 'z'; i++) {
            alphabet.append((char) i);
        }
        for (int i = '0'; i <= '9'; i++) {
            alphabet.append((char) i);
        }
        return alphabet.toString();
    }


    /**
     * Проверка ввода на целое число и знак
     *
     * @param sc
     * @param dialogMessage
     * @return int
     *
     */
    private static int getPosIntInput(Scanner sc, String dialogMessage) {
        int input;
        while (true) {
            System.out.print(dialogMessage);
            try {
                input = Integer.parseInt(sc.next());
                if (input < 0) {
                    System.out.println("Вы ввели отрицательное число, попробуйте еще раз");
                } else {
                    break;
                }
            } catch (NumberFormatException e) {
                System.out.println("Неверный ввод!");
            }
        }
        return input;
    }
}
