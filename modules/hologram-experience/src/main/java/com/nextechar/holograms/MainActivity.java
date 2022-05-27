package com.nextechar.holograms;

import android.content.Intent;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentOnAttachListener;

import com.google.ar.core.Anchor;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.Sceneform;
import com.google.ar.sceneform.rendering.Color;
import com.google.ar.sceneform.rendering.PlaneRenderer;
import com.google.ar.sceneform.ux.ArFragment;
import com.google.ar.sceneform.ux.BaseArFragment;
import com.google.ar.sceneform.ux.TransformableNode;
import com.google.ar.sceneform.ux.VideoNode;

import com.nextechar.holograms.R;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements
        MediaPlayer.OnPreparedListener,
        FragmentOnAttachListener,
        BaseArFragment.OnTapArPlaneListener {

    private final List<MediaPlayer> mediaPlayers = new ArrayList<>();
    private MediaPlayer holoPlayer;
    private ArFragment arFragment;
    private int mode = R.id.menuChromaKeyVideo;

    private Color chromaKeyColor;
    private Uri holoUri;
    private Boolean canPlaceHologram = false;
    private Boolean didPlaceHologram = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ViewCompat.setOnApplyWindowInsetsListener(toolbar, (v, insets) -> {
            ((ViewGroup.MarginLayoutParams) toolbar.getLayoutParams()).topMargin = insets
                    .getInsets(WindowInsetsCompat.Type.systemBars())
                    .top;

            return WindowInsetsCompat.CONSUMED;
        });

        getSupportFragmentManager().addFragmentOnAttachListener(this);

        if (savedInstanceState == null) {
            if (Sceneform.isSupported(this)) {
                getSupportFragmentManager().beginTransaction()
                        .add(R.id.arFragment, ArFragment.class, null)
                        .commit();
            }
        }

        chromaKeyColor = new Color(0.0f, 0.859f, 0.011f);

        // ATTENTION: This was auto-generated to handle app links.
        Intent appLinkIntent = getIntent();
        String appLinkAction = appLinkIntent.getAction();
        Uri appLinkData = appLinkIntent.getData();
        try {
            List<String> pathComponents = appLinkData.getPathSegments();
            int componentCount = pathComponents.size();
            String username = pathComponents.get(componentCount - 2);
            username = username.substring(2);
            Log.d("AppLink","username = " + username);
            String holoID = appLinkData.getLastPathSegment();
            Log.d("AppLink","holoID = " + holoID);
            holoUri = Uri.parse("https://holograms-cdn.nextechar.com/" + holoID + "/processed-web.mp4");
        } catch (Exception e) {
            holoUri = Uri.parse("https://holograms-cdn.nextechar.com/8c6cc476-baf8-4d5e-8859-8328946b0b87/processed-web.mp4");
        }
        holoPlayer = new MediaPlayer();
        try {
            holoPlayer.setDataSource(getApplicationContext(), holoUri);
            holoPlayer.setOnPreparedListener(this);
            holoPlayer.prepareAsync();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onAttachFragment(@NonNull FragmentManager fragmentManager, @NonNull Fragment fragment) {
        if (fragment.getId() == R.id.arFragment) {
            arFragment = (ArFragment) fragment;
            arFragment.setOnTapArPlaneListener(this);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.activity_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        item.setChecked(!item.isChecked());
        this.mode = item.getItemId();
        return true;
    }

    @Override
    protected void onStart() {
        super.onStart();

        for (MediaPlayer mediaPlayer : this.mediaPlayers) {
            mediaPlayer.start();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();

        for (MediaPlayer mediaPlayer : this.mediaPlayers) {
            mediaPlayer.pause();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        for (MediaPlayer mediaPlayer : this.mediaPlayers) {
            mediaPlayer.stop();
            mediaPlayer.reset();
        }
    }

    @Override
    public void onTapPlane(HitResult hitResult, Plane plane, MotionEvent motionEvent) {
        // Only allow one placement
        if (didPlaceHologram) {
            return;
        } else {
            didPlaceHologram = true;
        }

        // Only allow placement after player has loaded
        if (!canPlaceHologram)
        {
            return;
        }

        // Create the Anchor.
        Anchor anchor = hitResult.createAnchor();
        AnchorNode anchorNode = new AnchorNode(anchor);
        anchorNode.setParent(arFragment.getArSceneView().getScene());

        // Create the transformable model and add it to the anchor.
        TransformableNode modelNode = new TransformableNode(arFragment.getTransformationSystem());
        modelNode.setParent(anchorNode);

        // Uncomment for looping
        // holoPlayer.setLooping(true);

        VideoNode videoNode = new VideoNode(this, holoPlayer, chromaKeyColor, new VideoNode.Listener() {
            @Override
            public void onCreated(VideoNode videoNode) {
                holoPlayer.start();
            }

            @Override
            public void onError(Throwable throwable) {
                Toast.makeText(MainActivity.this, "Unable to load material", Toast.LENGTH_LONG).show();
            }
        });
        videoNode.setParent(modelNode);
        videoNode.setLocalScale(videoNode.getLocalScale().scaled(2.0f));

        // Disable the plane renderer
        PlaneRenderer planeRenderer = arFragment.getArSceneView().getPlaneRenderer();
        planeRenderer.setEnabled(false);

        // If you want that the VideoNode is always looking to the
        // Camera (You) comment the next line out. Use it mainly
        // if you want to display a Video. The use with activated
        // ChromaKey might look odd.
        //videoNode.setRotateAlwaysToCamera(true);

        modelNode.select();
    }

    @Override
    public void onPrepared(MediaPlayer mp) {
        canPlaceHologram = true;
    }
}
