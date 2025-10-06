package com.wintershade.opencvandroid;

import android.content.Context;
import android.opengl.GLSurfaceView;

public class MyGLSurfaceView extends GLSurfaceView {

    private final MyGLRenderer renderer;

    public MyGLSurfaceView(Context context) {
        super(context);
        setEGLContextClientVersion(2); // OpenGL ES 2.0
        renderer = new MyGLRenderer(context);
        setRenderer(renderer);
        setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
    }

    public MyGLRenderer getRenderer() {
        return renderer;
    }
}
