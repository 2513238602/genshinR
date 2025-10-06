import org.lwjgl.glfw.GLFWScrollCallbackI;
import org.lwjgl.system.MemoryStack;

import static org.lwjgl.glfw.GLFW.*;
import static java.lang.Math.*;

public class ArcballCamera {

    private final long window;

    // 目标点（围绕它旋转/平移）
    private final float[] target = new float[]{0f, 0f, 0f};

    // 相机与目标距离
    private float distance = 3.0f;
    private float minDistance = 0.05f;
    private float maxDistance = 1_000_000f;   // ↑ 提高默认可拉最远距离

    // 视图参数
    private float fovDeg = 60f;
    private float near = 0.02f, far = 10_000_000f; // ↑ 默认更远的 far

    // 鼠标交互
    private boolean rotating = false;
    private boolean panning  = false;

    // 鼠标状态
    private double lastX, lastY;
    private final float[] arcballV0 = new float[3];
    private final float[] arcballV1 = new float[3];

    // 旋转（四元数：w, x, y, z）
    private final float[] rot = new float[]{1, 0, 0, 0};

    // 速度参数
    private float panSpeed = 1.0f;        // 鼠标平移（随 distance 缩放）
    private float zoomSpeed = 0.12f;      // 滚轮指数缩放
    private float keyPanSpeed = 2.0f;     // 键盘平移（units/sec @ distance=1）
    private float keyZoomSpeed = 2.0f;    // 键盘指数缩放（per sec）
    private float keyRotSpeedDeg = 90f;   // 键盘旋转角速度（deg/sec）
    private float shiftMultiplier = 3.0f; // Shift 加速倍数

    // 键盘轮询的时间步
    private double lastTime;

    public ArcballCamera(long window) {
        this.window = window;
        installCallbacks();
        lastTime = glfwGetTime();
    }

    // ---------- 外部设置 ----------
    public void setTarget(float x, float y, float z) { target[0]=x; target[1]=y; target[2]=z; }
    public void setDistance(float d) {
        distance = clamp(d, minDistance, maxDistance);
        ensureFarForDistance(); // 自动把 far 拉远
    }
    public void setLimits(float minD, float maxD) { this.minDistance=minD; this.maxDistance=maxD; }
    public void setPanSpeed(float s) { this.panSpeed = s; }
    public void setKeySpeeds(float panUnitsPerSec, float zoomPerSec, float rotDegPerSec) {
        this.keyPanSpeed = panUnitsPerSec;
        this.keyZoomSpeed = zoomPerSec;
        this.keyRotSpeedDeg = rotDegPerSec;
    }
    public void setShiftMultiplier(float m) { this.shiftMultiplier = m; }
    public void setProjection(float fovDeg, float near, float far) {
        this.fovDeg = fovDeg; this.near = near; this.far = far;
        ensureFarForDistance();
    }

    /** 自动把 far（以及必要时 maxDistance）加大，避免看远时被裁掉/限制 */
    private void ensureFarForDistance() {
        float safety = 2.0f; // 距离的 2 倍作为 far 的安全余量
        if (distance * safety > far) {
            far = min(distance * safety, 1_000_000_000f); // 上限 1e9，避免溢出
        }
        if (distance > maxDistance * 0.9f) {
            maxDistance = min(maxDistance * 2.0f, 1_000_000_000f);
        }
    }

    /** 根据半径精确取景（留 20% 边界） */
    public void fitByRadius(float radius) {
        int width, height;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var w = stack.mallocInt(1); var h = stack.mallocInt(1);
            glfwGetFramebufferSize(window, w, h);
            width = Math.max(1, w.get(0));
            height = Math.max(1, h.get(0));
        }
        float aspect = (float) width / (float) height;
        float fovy = (float) toRadians(fovDeg);
        float fovx = 2f * (float) atan(tan(fovy / 2f) * aspect);

        float dzY = radius / (float) tan(fovy / 2f);
        float dzX = radius / (float) tan(fovx / 2f);
        float dist = Math.max(dzX, dzY);

        setDistance(dist * 1.2f);
    }
    public void frameMesh(float cx, float cy, float cz, float radius) {
        setTarget(cx, cy, cz);
        fitByRadius(Math.max(1e-6f, radius));
    }

    /** 每帧调用：键盘（WASD/QE/Shift + 方向键/Z/X 旋转） */
    public void update() {
        double now = glfwGetTime();
        float dt = (float) (now - lastTime);
        lastTime = now;
        if (dt <= 0f) return;

        boolean boosting = glfwGetKey(window, GLFW_KEY_LEFT_SHIFT) == GLFW_PRESS
                || glfwGetKey(window, GLFW_KEY_RIGHT_SHIFT) == GLFW_PRESS;
        float boost = boosting ? shiftMultiplier : 1.0f;

        // 基坐标
        float[] right = rotateVec3(rot, new float[]{1, 0, 0});
        float[] upVec = rotateVec3(rot, new float[]{0, 1, 0});
        normalize3(right); normalize3(upVec);

        // --- 平移: WASD ---
        int upKey = (glfwGetKey(window, GLFW_KEY_W) == GLFW_PRESS ? 1 : 0)
                - (glfwGetKey(window, GLFW_KEY_S) == GLFW_PRESS ? 1 : 0);
        int rightKey = (glfwGetKey(window, GLFW_KEY_D) == GLFW_PRESS ? 1 : 0)
                - (glfwGetKey(window, GLFW_KEY_A) == GLFW_PRESS ? 1 : 0);
        if (upKey != 0 || rightKey != 0) {
            float base = distance * keyPanSpeed * dt * boost;
            target[0] += (-rightKey) * base * right[0] + (upKey) * base * upVec[0];
            target[1] += (-rightKey) * base * right[1] + (upKey) * base * upVec[1];
            target[2] += (-rightKey) * base * right[2] + (upKey) * base * upVec[2];
        }

        // --- 缩放: Q/E ---
        int zoomKey = (glfwGetKey(window, GLFW_KEY_Q) == GLFW_PRESS ? 1 : 0)
                - (glfwGetKey(window, GLFW_KEY_E) == GLFW_PRESS ? 1 : 0);
        if (zoomKey != 0) {
            float factor = (float) exp(-zoomKey * keyZoomSpeed * dt * boost);
            setDistance(distance * factor);
        }

        // --- 旋转: 方向键（yaw/pitch） + Z/X（roll） ---
        float rotRad = (float) toRadians(keyRotSpeedDeg) * dt * boost;

        int yawKey   = (glfwGetKey(window, GLFW_KEY_LEFT)  == GLFW_PRESS ? 1 : 0)
                - (glfwGetKey(window, GLFW_KEY_RIGHT) == GLFW_PRESS ? 1 : 0);
        int pitchKey = (glfwGetKey(window, GLFW_KEY_UP)    == GLFW_PRESS ? 1 : 0)
                - (glfwGetKey(window, GLFW_KEY_DOWN)  == GLFW_PRESS ? 1 : 0);
        int rollKey  = (glfwGetKey(window, GLFW_KEY_Z)     == GLFW_PRESS ? 1 : 0)
                - (glfwGetKey(window, GLFW_KEY_X)     == GLFW_PRESS ? 1 : 0);

        if (yawKey != 0) {
            float[] axisY = new float[]{0, 1, 0}; // 世界Y轴，避免“歪头”
            applyAxisAngle(axisY, yawKey * rotRad);
        }
        if (pitchKey != 0) {
            float[] axisR = rotateVec3(rot, new float[]{1, 0, 0}); normalize3(axisR);
            applyAxisAngle(axisR, pitchKey * rotRad);
        }
        if (rollKey != 0) {
            float[] axisF = rotateVec3(rot, new float[]{0, 0, 1}); normalize3(axisF);
            applyAxisAngle(axisF, rollKey * rotRad);
        }

        // --- 重置旋转: R ---
        if (glfwGetKey(window, GLFW_KEY_R) == GLFW_PRESS) {
            rot[0] = 1; rot[1] = rot[2] = rot[3] = 0;
        }
    }

    // ---------- 取 VP ----------
    public float[] getViewProjection() {
        int width, height;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var w = stack.mallocInt(1);
            var h = stack.mallocInt(1);
            glfwGetFramebufferSize(window, w, h);
            width = max(1, w.get(0));
            height = max(1, h.get(0));
        }
        float aspect = (float) width / (float) height;

        float[] eye = rotateVec3(rot, new float[]{0, 0, distance});
        eye[0] += target[0]; eye[1] += target[1]; eye[2] += target[2];

        float[] up = rotateVec3(rot, new float[]{0, 1, 0});

        float[] view = Mat4f.lookAt(eye[0], eye[1], eye[2], target[0], target[1], target[2], up[0], up[1], up[2]);
        float[] proj = Mat4f.perspective(fovDeg, aspect, near, far);
        return Mat4f.multiply(proj, view);
    }

    // ---------- 鼠标/滚轮 ----------
    private void installCallbacks() {
        glfwSetMouseButtonCallback(window, (w, button, action, mods) -> {
            double x = getCursorX(), y = getCursorY();
            lastX = x; lastY = y;

            if (button == GLFW_MOUSE_BUTTON_LEFT) {
                rotating = (action == GLFW_PRESS);
                if (rotating) mapToSphere(x, y, arcballV0);
            } else if (button == GLFW_MOUSE_BUTTON_MIDDLE || button == GLFW_MOUSE_BUTTON_RIGHT) {
                panning = (action == GLFW_PRESS);
            }
        });

        glfwSetCursorPosCallback(window, (w, x, y) -> {
            if (rotating) {
                mapToSphere(x, y, arcballV1);
                applyArcballDrag(arcballV0, arcballV1);
                System.arraycopy(arcballV1, 0, arcballV0, 0, 3);
            } else if (panning) {
                panByPixelDelta(x - lastX, y - lastY);
            }
            lastX = x; lastY = y;
        });

        glfwSetScrollCallback(window, (GLFWScrollCallbackI) (w, xoff, yoff) -> {
            float factor = (float) exp(-yoff * zoomSpeed);
            setDistance(distance * factor);
        });
        // 键盘轮询在 update()，不再注册 key callback，避免冲突
    }

    // ---------- Arcball ----------
    private void mapToSphere(double mx, double my, float[] out) {
        int width, height;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var w = stack.mallocInt(1);
            var h = stack.mallocInt(1);
            glfwGetFramebufferSize(window, w, h);
            width = max(1, w.get(0));
            height = max(1, h.get(0));
        }
        double s = min(width, height);
        double x = (2.0 * mx - width)  / s;
        double y = (height - 2.0 * my) / s;
        double r2 = x*x + y*y;

        if (r2 <= 1.0) {
            out[0]=(float)x; out[1]=(float)y; out[2]=(float)sqrt(1.0 - r2);
        } else {
            double inv = 1.0 / sqrt(r2);
            out[0]=(float)(x*inv); out[1]=(float)(y*inv); out[2]=0f;
        }
    }
    private void applyArcballDrag(float[] v0, float[] v1) {
        float dot = clamp(dot(v0, v1), -1f, 1f);
        float angle = (float) acos(dot);
        float[] axis = cross(v0, v1);
        float len = length(axis);
        if (len < 1e-6f || angle == 0f) return;
        axis[0]/=len; axis[1]/=len; axis[2]/=len;
        applyAxisAngle(axis, angle);
    }

    // ---------- 键盘旋转帮助 ----------
    private void applyAxisAngle(float[] axis, float angle) {
        float half = angle * 0.5f;
        float s = (float) sin(half);
        float[] dq = new float[]{(float) cos(half), axis[0]*s, axis[1]*s, axis[2]*s};
        mulQuat(dq, rot, rot);
        normalizeQuat(rot);
    }

    private void panByPixelDelta(double dx, double dy) {
        int w, h;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var wi = stack.mallocInt(1);
            var hi = stack.mallocInt(1);
            glfwGetFramebufferSize(window, wi, hi);
            w = max(1, wi.get(0)); h = max(1, hi.get(0));
        }
        float scale = (float) (distance * panSpeed / min(w, h));
        float[] right = rotateVec3(rot, new float[]{1, 0, 0});
        float[] upVec = rotateVec3(rot, new float[]{0, 1, 0});
        target[0] -= (float) dx * scale * right[0] - (float) dy * scale * upVec[0];
        target[1] -= (float) dx * scale * right[1] - (float) dy * scale * upVec[1];
        target[2] -= (float) dx * scale * right[2] - (float) dy * scale * upVec[2];
    }

    // ---------- 小数学 ----------
    private static void normalize3(float[] v){
        float l = (float) sqrt(v[0]*v[0]+v[1]*v[1]+v[2]*v[2]);
        if (l>0){ v[0]/=l; v[1]/=l; v[2]/=l; }
    }
    private static float dot(float[] a, float[] b){ return a[0]*b[0]+a[1]*b[1]+a[2]*b[2]; }
    private static float length(float[] v){ return (float) sqrt(dot(v,v)); }
    private static float[] cross(float[] a, float[] b){
        return new float[]{ a[1]*b[2]-a[2]*b[1], a[2]*b[0]-a[0]*b[2], a[0]*b[1]-a[1]*b[0] };
    }
    private static float clamp(float x, float lo, float hi){ return x<lo?lo:(x>hi?hi:x); }

    private static void mulQuat(float[] q1, float[] q2, float[] out){
        float w1=q1[0], x1=q1[1], y1=q1[2], z1=q1[3];
        float w2=q2[0], x2=q2[1], y2=q2[2], z2=q2[3];
        out[0] = w1*w2 - x1*x2 - y1*y2 - z1*z2;
        out[1] = w1*x2 + x1*w2 + y1*z2 - z1*y2;
        out[2] = w1*y2 - x1*z2 + y1*w2 + z1*x2;
        out[3] = w1*z2 + x1*y2 - y1*x2 + z1*w2;
    }
    private static void normalizeQuat(float[] q){
        float l = (float) sqrt(q[0]*q[0]+q[1]*q[1]+q[2]*q[2]+q[3]*q[3]);
        if (l==0f){ q[0]=1; q[1]=q[2]=q[3]=0; return; }
        q[0]/=l; q[1]/=l; q[2]/=l; q[3]/=l;
    }
    private static float[] rotateVec3(float[] q, float[] v){
        float w=q[0], x=q[1], y=q[2], z=q[3];
        float tx = 2f * (y*v[2] - z*v[1]);
        float ty = 2f * (z*v[0] - x*v[2]);
        float tz = 2f * (x*v[1] - y*v[0]);
        return new float[]{
                v[0] + w*tx + (y*tz - z*ty),
                v[1] + w*ty + (z*tx - x*tz),
                v[2] + w*tz + (x*ty - y*tx)
        };
    }

    private double getCursorX(){ try (MemoryStack s = MemoryStack.stackPush()){ var x=s.mallocDouble(1); var y=s.mallocDouble(1); glfwGetCursorPos(window, x, y); return x.get(0);} }
    private double getCursorY(){ try (MemoryStack s = MemoryStack.stackPush()){ var x=s.mallocDouble(1); var y=s.mallocDouble(1); glfwGetCursorPos(window, x, y); return y.get(0);} }
}
