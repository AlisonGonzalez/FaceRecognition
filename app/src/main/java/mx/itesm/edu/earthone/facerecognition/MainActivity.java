package mx.itesm.edu.earthone.facerecognition;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.util.SparseArray;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.vision.Frame;
import com.google.android.gms.vision.face.Face;
import com.google.android.gms.vision.face.FaceDetector;
import com.google.android.gms.vision.face.Landmark;

import java.io.File;
import java.io.FileNotFoundException;

public class MainActivity extends AppCompatActivity {

    private static final String LOG_TAG = "FACE API";
    //Abrir la camara
    private static final int PHOTO_REQUEST = 10;
    //Ver la imagen que tomamos
    private TextView textView;
    private ImageView imageView;
    private Uri imageUri;
    private FaceDetector detector;

    //Banderas para guardar, escribir y guardar los resultados
    private static final int REQUEST_WRITE_PERMISSION = 20;
    private static final String SAVED_INSTANCE_URI = "uri";
    private static final String SAVED_INSTANCE_BITMAP = "bitmap";
    private static final String SAVED_INSTANCE_RESULT = "result";
    Bitmap editedBitmap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Button button = (Button) findViewById(R.id.button);
        textView = (TextView) findViewById(R.id.results);
        imageView = (ImageView) findViewById(R.id.scannedResults);
        if (savedInstanceState != null) {
            editedBitmap = savedInstanceState.getParcelable(SAVED_INSTANCE_BITMAP);
            if (savedInstanceState.getString(SAVED_INSTANCE_URI) != null) {
                imageUri = Uri.parse(savedInstanceState.getString(SAVED_INSTANCE_URI));
            }
            imageView.setImageBitmap(editedBitmap);
            textView.setText(savedInstanceState.getString(SAVED_INSTANCE_RESULT));
        }
        //Crear faceDetector
        //Crear la conexion
        detector = new FaceDetector.Builder(this)
                .setTrackingEnabled(true)
                .setLandmarkType(FaceDetector.ALL_LANDMARKS)
                .setClassificationType(FaceDetector.ALL_CLASSIFICATIONS)
                .build();

        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //Esto es solo para que pida los permisos
                ActivityCompat.requestPermissions(MainActivity.this,
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        REQUEST_WRITE_PERMISSION);
            }
        });

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case REQUEST_WRITE_PERMISSION:
                //Da el permiso
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    picTime();
                } else {
                    Toast.makeText(MainActivity.this, "Permission Denied!", Toast.LENGTH_SHORT).show();
                }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        //Se abre la galeria
        if (requestCode == PHOTO_REQUEST && resultCode == RESULT_OK) {
            launchCamera();
            //Se analiza esa foto
            try {
                faces();
            } catch (Exception e) {
                Toast.makeText(this, "Failed to load Image", Toast.LENGTH_SHORT).show();
                Log.e(LOG_TAG, e.toString());
            }
        }
    }

    //Hace todo el procesamiento de la imagen
    private void faces() throws Exception{
        //Vamos por la imagen que se obtiene
        Bitmap bitmap = decodeBitmapUri(this,imageUri);
        if (detector.isOperational() && bitmap != null){
            //Procesamiento de la imagen
            editedBitmap = Bitmap.createBitmap(bitmap.getWidth(),
                    bitmap.getHeight(),
                    bitmap.getConfig());

            float scale = getResources().getDisplayMetrics().density;
            //Para dibujar
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setColor(Color.rgb(255,102,102));
            paint.setTextSize(14*scale);
            //Dibujar unas 'pelotitas' dentro de los landmarks que reconoce
            paint.setShadowLayer(1f,0f,1f,Color.DKGRAY);
            //Definir brocha con la que se pintara
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(3f);

            Canvas canvas = new Canvas(editedBitmap);
            canvas.drawBitmap(bitmap,0,0,paint);

            //Para ligarlo
            Frame frame = new Frame.Builder().setBitmap(editedBitmap).build();
            //Vamos por un arreglo para ver cuantas caras hay
            SparseArray<Face> faces = detector.detect(frame);
            textView.setText(null);
            for (int i=0; i < faces.size();i++){
                Face face = faces.get(i);
                canvas.drawRect(face.getPosition().x,face.getPosition().y,
                        face.getPosition().x + face.getWidth(),face.getPosition().y+face.getHeight(),
                        paint);

                textView.setText(textView.getText() + "Face " + (i+1)+"\n");
                textView.setText(textView.getText() + "Smile " + "\n");
                textView.setText(textView.getText() + String.valueOf(face.getIsSmilingProbability()) +"");

                //Vamos a ver los landmarks
                for (Landmark landmark : face.getLandmarks()){
                    int lx = (int) (landmark.getPosition().x);
                    int ly = (int) (landmark.getPosition().y);
                    canvas.drawCircle(lx,ly,10,paint);
                }
            }

            //Validaciones
            //Si esta activo el detector, el bitmap (foto tomada)
            if (faces.size() == 0){
                Toast.makeText(this,"No hay caras",Toast.LENGTH_LONG).show();
            } else {
                imageView.setImageBitmap(editedBitmap);
                textView.setText(textView.getText() + "Faces "+
                        String.valueOf(faces.size())
                );
            }
        } else {
            Toast.makeText(this,"Detector not working",Toast.LENGTH_LONG).show();
        }
    }

    private void picTime() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        File photo = new File(Environment.getExternalStorageDirectory(), "picture.jpg");
        imageUri = FileProvider.getUriForFile(MainActivity.this,
                BuildConfig.APPLICATION_ID + ".provider", photo);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri);
        startActivityForResult(intent, PHOTO_REQUEST);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if (imageUri != null) {
            outState.putParcelable(SAVED_INSTANCE_BITMAP, editedBitmap);
            outState.putString(SAVED_INSTANCE_URI, imageUri.toString());
            outState.putString(SAVED_INSTANCE_RESULT, textView.getText().toString());
        }
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        //Para que cuando regrese, no truene
        detector.release();
    }

    private void launchCamera() {
        Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        mediaScanIntent.setData(imageUri);
        this.sendBroadcast(mediaScanIntent);
    }

    //Decodificar la imagen
    private Bitmap decodeBitmapUri(Context ctx, Uri uri) throws FileNotFoundException {
        int targetW = 600;
        int targetH = 600;
        BitmapFactory.Options bmOptions = new BitmapFactory.Options();
        bmOptions.inJustDecodeBounds = true;
        BitmapFactory.decodeStream(ctx.getContentResolver().openInputStream(uri), null, bmOptions);
        int photoW = bmOptions.outWidth;
        int photoH = bmOptions.outHeight;

        int scaleFactor = Math.min(photoW / targetW, photoH / targetH);
        bmOptions.inJustDecodeBounds = false;
        bmOptions.inSampleSize = scaleFactor;

        return BitmapFactory.decodeStream(ctx.getContentResolver()
                .openInputStream(uri), null, bmOptions);
    }
}