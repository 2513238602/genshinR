import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class MtlLoader {

    /** @param baseDir 以资源根为基准的目录，如 "aserts/Losalia/" */
    public static Map<String, Material> loadResource(String baseDir, String mtlFile) throws IOException {
        String resPath = join(baseDir, mtlFile);
        try (BufferedReader br = new BufferedReader(new InputStreamReader(
                Objects.requireNonNull(ClassLoader.getSystemClassLoader().getResourceAsStream(resPath),
                        "mtl resource not found: " + resPath),
                StandardCharsets.UTF_8))) {

            Map<String, Material> map = new LinkedHashMap<>();
            Material cur = null;
            String line;
            while ((line = br.readLine()) != null) {
                line = line.strip();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] p = line.split("\\s+");
                switch (p[0]) {
                    case "newmtl" -> {
                        cur = new Material();
                        cur.name = line.substring("newmtl".length()).trim();
                        map.put(cur.name, cur);
                    }
                    case "Kd" -> {
                        if (cur != null && p.length >= 4) {
                            cur.kdR = f(p[1]); cur.kdG = f(p[2]); cur.kdB = f(p[3]);
                        }
                    }
                    case "Ks" -> {
                        if (cur != null && p.length >= 4) {
                            cur.ksR = f(p[1]); cur.ksG = f(p[2]); cur.ksB = f(p[3]);
                        }
                    }
                    case "Ns" -> { if (cur != null && p.length >= 2) cur.shininess = f(p[1]); }
                    case "map_Kd" -> {
                        if (cur != null) {
                            // 处理含空格的路径/参数：取最后一个 token 作为文件名（够用）
                            String tex = p[p.length-1];
                            cur.mapKd = Texture2D.loadResource(join(baseDir, tex), true);
                        }
                    }
                    default -> {}
                }
            }
            return map;
        }
    }

    private static float f(String s){ return Float.parseFloat(s); }
    private static String join(String a, String b){
        if (a==null || a.isEmpty()) return b;
        a = a.endsWith("/") ? a : (a + "/");
        return a + (b.startsWith("/") ? b.substring(1) : b);
    }
}
