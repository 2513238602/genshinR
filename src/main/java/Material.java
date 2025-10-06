public class Material implements AutoCloseable {
    public String name;
    public float kdR=1, kdG=1, kdB=1;   // Kd
    public float ksR=0, ksG=0, ksB=0;   // Ks（先不用）
    public float shininess=16f;         // Ns（先不用）

    public Texture2D mapKd;             // 漫反射贴图
    // TODO: mapKs, normal 等可后续加

    public boolean hasMapKd(){ return mapKd != null; }

    @Override public void close() {
        if (mapKd != null) mapKd.close();
    }
}
