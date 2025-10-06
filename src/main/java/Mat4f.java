public final class Mat4f {
    private Mat4f() {}

    /** 单位矩阵（列主序） */
    public static float[] identity() {
        float[] m = new float[16];
        m[0]=1; m[5]=1; m[10]=1; m[15]=1;
        return m;
    }

    /** r = a * b （列主序） */
    public static float[] multiply(float[] a, float[] b) {
        float[] r = new float[16];
        for (int c=0; c<4; c++) {
            for (int r0=0; r0<4; r0++) {
                r[c*4 + r0] = a[0*4+r0]*b[c*4+0]
                        + a[1*4+r0]*b[c*4+1]
                        + a[2*4+r0]*b[c*4+2]
                        + a[3*4+r0]*b[c*4+3];
            }
        }
        return r;
    }

    public static float[] translate(float x, float y, float z) {
        float[] m = identity();
        m[12]=x; m[13]=y; m[14]=z;
        return m;
    }

    public static float[] scale(float s) {
        float[] m = identity();
        m[0]=s; m[5]=s; m[10]=s;
        return m;
    }

    public static float[] rotateY(float rad) {
        float c=(float)Math.cos(rad), s=(float)Math.sin(rad);
        float[] m = identity();
        m[0]= c; m[2]= s;
        m[8]=-s; m[10]=c;
        return m;
    }

    public static float[] perspective(float fovDeg, float aspect, float near, float far) {
        float f = 1f/(float)Math.tan(Math.toRadians(fovDeg)/2.0);
        float[] m = new float[16];
        m[0]=f/aspect; m[5]=f; m[10]=(far+near)/(near-far); m[11]=-1f;
        m[14]=(2f*far*near)/(near-far);
        return m;
    }

    public static float[] lookAt(float ex,float ey,float ez,
                                 float cx,float cy,float cz,
                                 float ux,float uy,float uz) {
        float fx=cx-ex, fy=cy-ey, fz=cz-ez;
        float fl=(float)Math.sqrt(fx*fx+fy*fy+fz*fz); fx/=fl; fy/=fl; fz/=fl;
        float sx = fy*uz - fz*uy, sy = fz*ux - fx*uz, sz = fx*uy - fy*ux;
        float sl=(float)Math.sqrt(sx*sx+sy*sy+sz*sz); sx/=sl; sy/=sl; sz/=sl;
        float ux2 = sy*fz - sz*fy, uy2 = sz*fx - sx*fz, uz2 = sx*fy - sy*fx;

        float[] m = new float[16];
        m[0]=sx;  m[4]=ux2; m[8] =-fx; m[12]=0;
        m[1]=sy;  m[5]=uy2; m[9] =-fy; m[13]=0;
        m[2]=sz;  m[6]=uz2; m[10]=-fz; m[14]=0;
        m[3]=0;   m[7]=0;   m[11]=0;  m[15]=1;

        return multiply(m, translate(-ex, -ey, -ez));
    }
}
