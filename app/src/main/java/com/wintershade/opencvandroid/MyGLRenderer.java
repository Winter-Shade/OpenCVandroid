package com.wintershade.opencvandroid;

import android.content.Context;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.opengles.GL10;
import javax.microedition.khronos.egl.EGLConfig;

public class MyGLRenderer implements GLSurfaceView.Renderer {
    private static final String TAG = "MyGLRenderer";

    private final Context context;

    // simple textured quad
    private final float[] squareCoords = {
            -1.0f,  1.0f,
            -1.0f, -1.0f,
            1.0f, -1.0f,
            1.0f,  1.0f
    };

    private final float[] textureCoords = {
            0.0f, 0.0f,
            0.0f, 1.0f,
            1.0f, 1.0f,
            1.0f, 0.0f
    };

    private FloatBuffer vertexBuffer, texBuffer;
    private int program;
    private int positionHandle, texCoordHandle, samplerHandle;

    private int textureId = -1;

    // Buffer update mechanism
    private ByteBuffer pendingPixelBuffer = null;
    private int pendingWidth = 0, pendingHeight = 0;
    private final Object bufferLock = new Object();

    private final String vertexShaderCode =
            "attribute vec4 aPosition;" +
                    "attribute vec2 aTexCoord;" +
                    "varying vec2 vTexCoord;" +
                    "void main() {" +
                    "  gl_Position = aPosition;" +
                    "  vTexCoord = vec2(aTexCoord.y, 1.0 - aTexCoord.x);" +
                    "}";

    private final String fragmentShaderCode =
            "precision mediump float;\n" +
                    "varying vec2 vTexCoord;\n" +
                    "uniform sampler2D uTexture;\n" +
                    "void main() {\n" +
                    "  gl_FragColor = texture2D(uTexture, vTexCoord);\n" +
                    "}";

    public MyGLRenderer(Context ctx) {
        this.context = ctx;

        ByteBuffer bb = ByteBuffer.allocateDirect(squareCoords.length * 4).order(ByteOrder.nativeOrder());
        vertexBuffer = bb.asFloatBuffer();
        vertexBuffer.put(squareCoords);
        vertexBuffer.position(0);

        ByteBuffer tb = ByteBuffer.allocateDirect(textureCoords.length * 4).order(ByteOrder.nativeOrder());
        texBuffer = tb.asFloatBuffer();
        texBuffer.put(textureCoords);
        texBuffer.position(0);
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        program = createProgram(vertexShaderCode, fragmentShaderCode);
        positionHandle = GLES20.glGetAttribLocation(program, "aPosition");
        texCoordHandle = GLES20.glGetAttribLocation(program, "aTexCoord");
        samplerHandle = GLES20.glGetUniformLocation(program, "uTexture");

        GLES20.glClearColor(0f, 0f, 0f, 1f);

        // create texture
        int[] tex = new int[1];
        GLES20.glGenTextures(1, tex, 0);
        textureId = tex[0];
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId);
        // params
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

        // leave texture blank until first upload
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        GLES20.glViewport(0, 0, width, height);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

        // If there's a pending pixel buffer, upload it to the texture
        synchronized (bufferLock) {
            if (pendingPixelBuffer != null && pendingWidth > 0 && pendingHeight > 0) {
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId);
                pendingPixelBuffer.position(0);
                GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,
                        pendingWidth, pendingHeight, 0,
                        GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, pendingPixelBuffer);
                // clear pending buffer reference (we keep the ByteBuffer for reuse GC-wise)
                pendingPixelBuffer = null;
            }
        }

        GLES20.glUseProgram(program);

        vertexBuffer.position(0);
        GLES20.glEnableVertexAttribArray(positionHandle);
        GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer);

        texBuffer.position(0);
        GLES20.glEnableVertexAttribArray(texCoordHandle);
        GLES20.glVertexAttribPointer(texCoordHandle, 2, GLES20.GL_FLOAT, false, 0, texBuffer);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId);
        GLES20.glUniform1i(samplerHandle, 0);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_FAN, 0, 4);

        GLES20.glDisableVertexAttribArray(positionHandle);
        GLES20.glDisableVertexAttribArray(texCoordHandle);
    }

    private int loadShader(int type, String shaderSrc) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, shaderSrc);
        GLES20.glCompileShader(shader);
        // check compile status
        int[] compiled = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);
        if (compiled[0] == 0) {
            String err = GLES20.glGetShaderInfoLog(shader);
            Log.e(TAG, "Shader compile error: " + err);
            GLES20.glDeleteShader(shader);
            return 0;
        }
        return shader;
    }

    private int createProgram(String vsrc, String fsrc) {
        int v = loadShader(GLES20.GL_VERTEX_SHADER, vsrc);
        int f = loadShader(GLES20.GL_FRAGMENT_SHADER, fsrc);
        int prog = GLES20.glCreateProgram();
        GLES20.glAttachShader(prog, v);
        GLES20.glAttachShader(prog, f);
        GLES20.glLinkProgram(prog);
        int[] linkStatus = new int[1];
        GLES20.glGetProgramiv(prog, GLES20.GL_LINK_STATUS, linkStatus, 0);
        if (linkStatus[0] != GLES20.GL_TRUE) {
            String err = GLES20.glGetProgramInfoLog(prog);
            Log.e(TAG, "Program link error: " + err);
            GLES20.glDeleteProgram(prog);
            return 0;
        }
        return prog;
    }

    public void updateTexture(ByteBuffer rgbaBuffer, int width, int height) {
        // We expect direct buffer in native order
        ByteBuffer copy = ByteBuffer.allocateDirect(rgbaBuffer.capacity()).order(ByteOrder.nativeOrder());
        rgbaBuffer.position(0);
        copy.put(rgbaBuffer);
        copy.position(0);

        synchronized (bufferLock) {
            pendingPixelBuffer = copy;
            pendingWidth = width;
            pendingHeight = height;
        }
    }
}
