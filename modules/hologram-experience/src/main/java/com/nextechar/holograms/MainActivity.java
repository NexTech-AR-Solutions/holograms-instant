package com.nextechar.holograms;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
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
import com.google.ar.sceneform.ArSceneView;
import com.google.ar.sceneform.Sceneform;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.Color;
import com.google.ar.sceneform.rendering.PlaneRenderer;
import com.google.ar.sceneform.ux.ArFragment;
import com.google.ar.sceneform.ux.BaseArFragment;
import com.google.ar.sceneform.ux.TransformableNode;
import com.google.ar.sceneform.ux.VideoNode;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.Transformation;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import jp.wasabeef.picasso.transformations.CropCircleTransformation;
import jp.wasabeef.picasso.transformations.RoundedCornersTransformation;

public class MainActivity extends AppCompatActivity implements
        MediaPlayer.OnPreparedListener,
        MediaPlayer.OnCompletionListener,
        PlaneRenderer.PlaneRendererListener,
        FragmentOnAttachListener,
        BaseArFragment.OnTapArPlaneListener,
        ArFragment.OnViewCreatedListener {

    private final List<MediaPlayer> mediaPlayers = new ArrayList<>();
    private MediaPlayer holoPlayer;
    private MediaMetadataRetriever holoMetadata;
    private ArFragment arFragment;
    private View ctaLayout;
    private float ctaOffset;
    private Button download;
    private Button notNow;
    private int mode = R.id.menuChromaKeyVideo;

    private Color chromaKeyColor;
    private Uri holoUri;
    private Uri profileUri;
    private String holoID;
    private String creatorID;
    private Boolean canPlaceHologram = false;
    private Boolean didPlaceHologram = false;

    private float anchorOffset;
    private float frameScale;
    private float height = 1.68f; // Average height of US population in meters

    private HologramsServer server;
    private Boolean connectedToServer = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setupServer();

        setContentView(R.layout.activity_main);
//        Toolbar toolbar = findViewById(R.id.toolbar);
//        setSupportActionBar(toolbar);
//        ViewCompat.setOnApplyWindowInsetsListener(toolbar, (v, insets) -> {
//            ((ViewGroup.MarginLayoutParams) toolbar.getLayoutParams()).topMargin = insets
//                    .getInsets(WindowInsetsCompat.Type.systemBars())
//                    .top;
//
//            return WindowInsetsCompat.CONSUMED;
//        });

        ctaLayout = ((View)findViewById(R.id.cta_layout));

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
            String linkHoloID = appLinkData.getLastPathSegment();
            Log.d("AppLink","holoID = " + linkHoloID);
            holoUri = Uri.parse("https://holograms-cdn.nextechar.com/" +
                    linkHoloID + "/processed-web.mp4");
            holoID = linkHoloID;
            creatorID = username;
        } catch (Exception e) {
            // Display a welcome Hologram if the user is following a bad link
            holoUri = Uri.parse("https://holograms-cdn.nextechar.com/" +
                    "8c6cc476-baf8-4d5e-8859-8328946b0b87/processed-web.mp4");
            holoID = "8c6cc476-baf8-4d5e-8859-8328946b0b87";
            creatorID = "mp";
        }

        // Get creator profile picture
        // setupProfileImage(creatorID);
        // Set profile name
        TextView profileName = findViewById(R.id.profile_name);
        profileName.setText(creatorID);
        ImageView profileImage = findViewById(R.id.profile_image);
        //profileImage.setImageResource(R.drawable.profile_placeholder);
        String profileUriString = "https://profiles-cdn.nextechar.com/"
                + creatorID + "/userprofile.png";

        Picasso.get()
                .load(Uri.parse(profileUriString))
                .transform(new HologramsCropCircleTransformation())
                .placeholder(R.drawable.profile_placeholder)
                .error(R.drawable.profile_placeholder)
                .into(profileImage);

        holoPlayer = new MediaPlayer();
        try {
            holoPlayer.setDataSource(getApplicationContext(), holoUri);
            holoPlayer.setOnPreparedListener(this);
            holoPlayer.setOnCompletionListener(this);
            holoPlayer.prepareAsync();

            calculateNormalizationConstants(holoUri);

            holoPlayer.setOnCompletionListener(this);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void setupServer()
    {
        Thread networkThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try  {
                    server = new HologramsServer("guest","password");
                    connectedToServer = server.login(server.username, server.password);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        networkThread.start();
    }

    private void setupProfileImage(String username)
    {
        // Get a handler that can be used to post to the main thread
        Handler mainHandler = new Handler(Looper.getMainLooper());
        Runnable updateProfileDisplay = new Runnable() {
            @Override
            public void run() {
                Log.d("server", "profile Uri: " + profileUri.toString());
                ImageView profileImage = (ImageView)findViewById(R.id.profile_image);
                Picasso.get()
                        .load(profileUri)
                        .placeholder(R.drawable.holograms_circle_icon)
                        .error(R.drawable.profile_placeholder)
                        .into(profileImage);
            }
        };

        Thread networkThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try  {
                    String filepath = server.getUserProfileImage(username);
                    profileUri = Uri.parse(filepath);
                    mainHandler.post(updateProfileDisplay);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        networkThread.start();
    }

    public static float dpFromPx(final Context context, final float px) {
        return px / context.getResources().getDisplayMetrics().density;
    }

    public static float pxFromDp(final Context context, final float dp) {
        return dp * context.getResources().getDisplayMetrics().density;
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);

        // Animate the layout offscreen immediately
        Log.d("ctaLayout height", "height: " + Integer.toString(ctaLayout.getHeight()));
        // App icon size = 50dp + 20dp margins above and below gives us a 90dp offset for the icon
        float iconOffset = 90;
        ctaOffset = ctaLayout.getHeight()-pxFromDp(this, iconOffset);
        ObjectAnimator animation = ObjectAnimator.ofFloat(ctaLayout, "translationY", ctaOffset);
        animation.setDuration(100);
        animation.start();
    }

    // This method has the side effects of setting the class variables: anchorOffset,
    //                                                                  frameScale &
    //                                                                  holoMetadata
    // For our purposes, Uri uri should be a web-based mp4 video file.
    public void calculateNormalizationConstants(Uri uri)
    {
        holoMetadata = new MediaMetadataRetriever();
        holoMetadata.setDataSource(uri.toString(), new HashMap<String,String>());
        long durationMs = Long.parseLong(holoMetadata.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_DURATION));

        Bitmap searchSpace = holoMetadata.getScaledFrameAtTime(durationMs*1000/2,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                64,
                128);
        int pixelsLength = 64*searchSpace.getHeight();
        int pixels[] = new int[pixelsLength];
        searchSpace.getPixels(pixels,0,64,0,0,64,searchSpace.getHeight());

        int topRow = 0;
        int bottomRow = searchSpace.getHeight();
        for (int i = 0; i < pixelsLength; i++)
        {
            Color test = new Color(pixels[i]);
            if (test.r != 0.0 ||
                    test.g != 1.0 ||
                    test.b != 0.0)
            {
                topRow = i/64;
                break;
            }
        }
        for (int i = pixelsLength - 1; i > 0; i--)
        {
            Color test = new Color(pixels[i]);
            if (test.r != 0.0 ||
                    test.g != 1.0 ||
                    test.b != 0.0)
            {
                bottomRow = i/64;
                break;
            }
        }
        Log.d("RowData","TopRow = " + topRow + " / BottomRow = " + bottomRow);
        float frameHeight = searchSpace.getHeight();
        // percentage of frame occupied by subject
        frameScale = (bottomRow - topRow) / frameHeight;
        // percentage of frame by which the anchor must be offset
        anchorOffset = (frameHeight - bottomRow) / frameHeight;
    }

    @Override
    public void onAttachFragment(@NonNull FragmentManager fragmentManager,
                                 @NonNull Fragment fragment) {
        if (fragment.getId() == R.id.arFragment) {
            arFragment = (ArFragment) fragment;
            arFragment.setOnTapArPlaneListener(this);
            arFragment.setOnViewCreatedListener(this);
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

        holoPlayer.start();

    }

    @Override
    public void onPrepared(MediaPlayer mp) {
        canPlaceHologram = true;
    }

    @Override
    public void onPlaneRendererUpdate(PlaneRenderer planeRenderer) {
        // Get the plane renderer focus point
        //PlaneRenderer planeRenderer = arFragment.getArSceneView().getPlaneRenderer();
        HitResult focus = planeRenderer.getLastHitResult();

        // Place the hologram if we haven't already
        if (!didPlaceHologram && (focus != null))
        {
            placeHologram(focus);
            didPlaceHologram = true;
        }
        // Otherwise, move the anchor to follow focus

    }

    public void placeHologram(HitResult focus)
    {
        // Create the Anchor.
        Anchor anchor = focus.createAnchor();
        AnchorNode anchorNode = new AnchorNode(anchor);
        anchorNode.setParent(arFragment.getArSceneView().getScene());

        // Create the transformable model and add it to the anchor.
        TransformableNode modelNode = new TransformableNode(arFragment.getTransformationSystem());
        modelNode.setParent(anchorNode);

        // Uncomment for looping
        // holoPlayer.setLooping(true);

        VideoNode videoNode =
                new VideoNode(this, holoPlayer, chromaKeyColor, new VideoNode.Listener() {
            @Override
            public void onCreated(VideoNode videoNode) {
                //holoPlayer.start();
            }

            @Override
            public void onError(Throwable throwable) {
                Toast.makeText(MainActivity.this,
                        "Unable to load material",
                        Toast.LENGTH_LONG).show();
            }
        });
        videoNode.setParent(modelNode);
        Vector3 videoPosition = videoNode.getLocalPosition();
        videoPosition.z = videoPosition.z - anchorOffset;
        videoNode.setLocalPosition(videoPosition);
        videoNode.setLocalScale(videoNode.getLocalScale().scaled(1.0f/frameScale * height));

        // If you want that the VideoNode is always looking to the
        // Camera (You) comment the next line out. Use it mainly
        // if you want to display a Video. The use with activated
        // ChromaKey might look odd.
        //videoNode.setRotateAlwaysToCamera(true);

        // Disable the plane renderer
        PlaneRenderer planeRenderer = arFragment.getArSceneView().getPlaneRenderer();
        planeRenderer.setEnabled(false);

        modelNode.select();
        holoPlayer.start();
    }

    @Override
    public void onViewCreated(ArSceneView arSceneView) {
        PlaneRenderer planeRenderer = arFragment.getArSceneView().getPlaneRenderer();
        planeRenderer.setPlaneRendererListener(this);
    }

    private void debugAnchorPlacement(HitResult position)
    {
        // Create the Anchor.
        Anchor anchor = position.createAnchor();
        AnchorNode anchorNode = new AnchorNode(anchor);
        anchorNode.setParent(arFragment.getArSceneView().getScene());

        // Create the transformable model and add it to the anchor.
        TransformableNode modelNode = new TransformableNode(arFragment.getTransformationSystem());
        modelNode.setParent(anchorNode);

        // Uncomment for looping
        // holoPlayer.setLooping(true);

        VideoNode videoNode =
                new VideoNode(this, holoPlayer, chromaKeyColor, new VideoNode.Listener() {
            @Override
            public void onCreated(VideoNode videoNode) {
                holoPlayer.start();
            }

            @Override
            public void onError(Throwable throwable) {
                Toast.makeText(MainActivity.this,
                        "Unable to load material",
                        Toast.LENGTH_LONG).show();
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
    public void onCompletion(MediaPlayer mp) {

        // Create app-download call-to-action view
        download  = ((Button)findViewById(R.id.download_button));
        notNow    = ((Button)findViewById(R.id.notnow_button));

        View.OnClickListener downloadListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Open Link to Play Store
                final String appPackageName = "com.nextechar.holograms"; // getPackageName() from Context or Activity object
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + appPackageName)));
                } catch (android.content.ActivityNotFoundException anfe) {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + appPackageName)));
                }
            }
        };

        View.OnClickListener notNowListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ctaLayout.setVisibility(View.INVISIBLE);
                notNow.setEnabled(false);
                download.setEnabled(false);
            }
        };

        download.setOnClickListener(downloadListener);
        download.setEnabled(true);

        notNow.setOnClickListener(notNowListener);
        notNow.setEnabled(true);

        // Animate the call-to-action layout onscreen
        ObjectAnimator animation = ObjectAnimator.ofFloat(ctaLayout, "translationY", 0);
        animation.setDuration(500);
        animation.start();

        // Animate view into position


    }


}
