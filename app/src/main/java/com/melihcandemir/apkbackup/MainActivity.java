package com.melihcandemir.apkbackup;
import androidx.appcompat.app.AlertDialog;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.viewpager.widget.ViewPager;
import androidx.viewpager2.widget.ViewPager2;

import android.view.View;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.Manifest;

public class MainActivity extends AppCompatActivity {
    private static final int BUFFER_SIZE = 1024;
    private static final String BACKUP_DIR_NAME = "app_backups";
    private static final String BACKUP_FILE_EXTENSION = ".apk";
    private ListView listView;
    private List<ApplicationInfo> installedApps;
    private Map<String, Object> packageNameAppInfoMap;
    private TabLayout tabLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        listView = getDelegate().findViewById(R.id.appList);

        packageNameAppInfoMap = new HashMap<>();

        installedApps = getInstalledApps();

        ArrayAdapter<ApplicationInfo> adapter = new ArrayAdapter<ApplicationInfo>(this, R.layout.list_item, R.id.appNameTextView, installedApps) {
            @NonNull
            @Override
            public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                View view = super.getView(position, convertView, parent);

                ImageView appIconImageView = view.findViewById(R.id.appIconImageView);
                TextView appNameTextView = view.findViewById(R.id.appNameTextView);

                ApplicationInfo appInfo = installedApps.get(position);
                Drawable appIcon = appInfo.loadIcon(getPackageManager());
                String appLabel = appInfo.loadLabel(getPackageManager()).toString();

                appIconImageView.setImageDrawable(appIcon);
                appNameTextView.setText(appLabel);

                // Set the background color based on whether a backup file exists
                appNameTextView.setTextColor(hasBackupFile(appInfo) ? Color.rgb(2, 179, 118) :
                        Color.rgb(178, 120, 220));

                return view;
            }
        };

        listView.setAdapter(adapter);
        listView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);

        getDelegate().findViewById(R.id.backupButton).setOnClickListener(v -> backupSelectedApp());

        Button refreshButton = getDelegate().findViewById(R.id.refreshButton);
        refreshButton.setOnClickListener(v -> onRefreshButtonClick(v));

        Button deleteBackupButton = getDelegate().findViewById(R.id.deleteBackupButton);
        deleteBackupButton.setOnClickListener(v -> onDeleteBackupButtonClick(v));

        Button restoreButton = getDelegate().findViewById(R.id.restoreButton);
        restoreButton.setOnClickListener(v -> onRestoreButtonClick(v));

        getDelegate().findViewById(R.id.shareButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                shareApkFile();
            }
        });

        restoreButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // İzin kontrolü yapmadan klasörden paket yükleme ekranını göster
                File backupFile = new File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), BACKUP_DIR_NAME + "/" + getSelectedAppPackageName() + BACKUP_FILE_EXTENSION);

                showPackageInstallScreen(backupFile);
                Log.d("BackupApp", "Backup File Path: " + backupFile.getAbsolutePath());
                if (backupFile.exists()) {
                    Log.d("RestoreApp", "Backup File Exists");
                } else {
                    Log.e("RestoreApp", "Backup File Does Not Exist");
                }
            }
        });

        // Assuming you have references to TabLayout and ViewPager2
        TabLayout tabLayout = findViewById(R.id.tabLayout);
        ViewPager2 viewPager = findViewById(R.id.viewPager2);

        // Create ArchivedAppsFragment on demand
        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                if (position == 1) { // Check if it's the archived tab
                    // Create and add ArchivedAppsFragment
                    if (getSupportFragmentManager().findFragmentById(R.id.viewPager2) == null) {
                        Fragment archivedFragment = new ArchivedAppsFragment();
                        // Add the fragment to the ViewPager2 using a FragmentTransaction
                        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
                        transaction.add(R.id.viewPager2, archivedFragment);
                        transaction.commitNow();
                    }
                }
            }
        });
    }

    @Override
    public void onBackPressed() {
        ViewPager2 viewPager = findViewById(R.id.viewPager2);
        if (viewPager.getCurrentItem() == 1) {
            // Attempt to remove the ArchivedAppsFragment
            Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.viewPager2);
            if (fragment != null) {
                FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
                transaction.remove(fragment);
                transaction.commitNow();
            }
            // Set the current item back to 0 (main activity)
            viewPager.setCurrentItem(0);
        } else {
            super.onBackPressed();
        }
    }

    private void showPackageInstallScreen(File apkFile) {
        // Create an intent to install the APK
        Intent installIntent = new Intent(Intent.ACTION_VIEW);
        Uri apkUri = FileProvider.getUriForFile(
                this,
                "com.melihcandemir.apkbackup.fileprovider",
                apkFile);

        installIntent.setDataAndType(apkUri, "application/vnd.android.package-archive");
        installIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        // Log the installation intent details
        Log.d("InstallIntent", "Data URI: " + installIntent.getData());
        Log.d("InstallIntent", "Type: " + installIntent.getType());
        Log.d("InstallIntent", "Flags: " + installIntent.getFlags());

        // Check if there are apps available to handle the install intent
        if (installIntent.resolveActivity(getPackageManager()) != null) {
            try {
                // Start the installation intent
                startActivity(installIntent);
                Log.d("InstallIntent", "Installation started successfully");
            } catch (Exception e) {
                // Handle any exception that might occur during installation
                Log.e("InstallIntent", "Installation error: " + e.getMessage());
                Toast.makeText(this, "Installation error", Toast.LENGTH_SHORT).show();
            }
        } else {
            // Handle the case where no app can handle the install intent
            Log.e("InstallIntent", "No app available to install the package");
            Toast.makeText(this, "No app available to install the package", Toast.LENGTH_SHORT).show();
        }
    }



    private String getSelectedAppPackageName() {
        int selectedPosition = listView.getCheckedItemPosition();
        if (selectedPosition != ListView.INVALID_POSITION) {
            ApplicationInfo selectedAppInfo = installedApps.get(selectedPosition);
            return selectedAppInfo.packageName;
        }
        return null;  // veya başka bir değer döndürebilirsiniz, bu duruma göre
    }

    private List<ApplicationInfo> getInstalledApps() {
        List<ApplicationInfo> installedAppsList = new ArrayList<>();
        List<String> packageNames = new ArrayList<>();

        PackageManager packageManager = getPackageManager();
        List<PackageInfo> packages = packageManager.getInstalledPackages(PackageManager.GET_META_DATA);

        for (PackageInfo packageInfo : packages) {
            if ((packageInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) == 0) {
                installedAppsList.add(packageInfo.applicationInfo);
                packageNames.add(packageInfo.packageName);
                packageNameAppInfoMap.put(packageInfo.packageName, packageInfo.applicationInfo);
            }
        }

        // Add uninstalled apps with backups
        List<String> backupPackageNames = getBackupPackageNames();
        for (String packageName : backupPackageNames) {
            if (!packageNames.contains(packageName)) {
                try {
                    ApplicationInfo appInfo = packageManager.getApplicationInfo(packageName, 0);
                    installedAppsList.add(appInfo);
                    packageNames.add(packageName);
                    packageNameAppInfoMap.put(packageName, appInfo);
                } catch (PackageManager.NameNotFoundException e) {
                    e.printStackTrace();
                    // Handle the exception if needed
                }
            }
        }

        this.installedApps = installedAppsList;
        return installedAppsList;
    }

    private List<String> getBackupPackageNames() {
        List<String> packageNames = new ArrayList<>();
        File backupDir = new File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), BACKUP_DIR_NAME);

        if (backupDir.exists()) {
            for (File file : backupDir.listFiles()) {
                if (file.isFile() && file.getName().endsWith(BACKUP_FILE_EXTENSION)) {
                    String packageName = file.getName().replace(BACKUP_FILE_EXTENSION, "");
                    packageNames.add(packageName);
                }
            }
        }

        return packageNames;
    }


    private void backupSelectedApp() {
        int selectedPosition = listView.getCheckedItemPosition();
        if (selectedPosition != ListView.INVALID_POSITION) {
            ApplicationInfo selectedAppInfo = installedApps.get(selectedPosition);
            String selectedAppPackageName = selectedAppInfo.packageName;

            Log.d("BackupApp", "Selected App: " + selectedAppPackageName);

            File backupDir = new File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), BACKUP_DIR_NAME);

            if (!backupDir.exists()) {
                backupDir.mkdirs();
            }

            String backupFileName = selectedAppPackageName + BACKUP_FILE_EXTENSION;
            File backupFile = new File(backupDir, backupFileName);

            PackageManager packageManager = getPackageManager();
            ApplicationInfo appInfo = null;
            try {
                appInfo = packageManager.getApplicationInfo(selectedAppPackageName, 0);
            } catch (PackageManager.NameNotFoundException e) {
                e.printStackTrace();
            }

            if (appInfo != null) {
                try {
                    backupApp(appInfo, backupFile);
                    Toast.makeText(this, selectedAppPackageName + " successfully backed up", Toast.LENGTH_SHORT).show();
                } catch (IOException e) {
                    e.printStackTrace();
                    Toast.makeText(this, "Backup Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "Please choose a non-system application", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private String getPackageNameFromAppName(String appName) {
        PackageManager packageManager = getPackageManager();
        List<PackageInfo> packages = packageManager.getInstalledPackages(PackageManager.GET_META_DATA);

        for (PackageInfo packageInfo : packages) {
            CharSequence label = packageInfo.applicationInfo.loadLabel(packageManager);
            if (label.toString().startsWith(appName)) {
                return packageInfo.packageName;
            }
        }

        return null;
    }

    private void backupApp(ApplicationInfo appInfo, File backupFile) throws IOException {
        PackageManager packageManager = getPackageManager();

        if (appInfo != null) {
            File sourceFile = new File(appInfo.sourceDir);
            File destinationFile = backupFile;

            if (sourceFile.exists()) {
                try (InputStream inputStream = new FileInputStream(sourceFile);
                     OutputStream outputStream = new FileOutputStream(destinationFile)) {

                    byte[] buffer = new byte[BUFFER_SIZE];
                    int length;
                    while ((length = inputStream.read(buffer)) > 0) {
                        outputStream.write(buffer, 0, length);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    throw e;
                }
            } else {
                Log.e("BackupApp", "Application source file not found");
                throw new IOException("Application source file not found");
            }
        } else {
            Log.e("BackupApp", "Application not found");
            throw new IOException("Application not found");
        }
    }

    private boolean hasBackupFile(ApplicationInfo appInfo) {
        File backupDir = new File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), BACKUP_DIR_NAME);
        if (!backupDir.exists()) {
            return false;
        }

        String packageName = appInfo.packageName;
        String backupFileName = packageName + BACKUP_FILE_EXTENSION;
        File backupFile = new File(backupDir, backupFileName);

        return backupFile.exists();
    }

    private boolean hasBackupFileForSelectedApp() {
        int selectedPosition = listView.getCheckedItemPosition();
        if (selectedPosition != ListView.INVALID_POSITION) {
            ApplicationInfo selectedAppInfo = installedApps.get(selectedPosition);
            return hasBackupFile(selectedAppInfo);
        }
        return false;
    }

    public void onRefreshButtonClick(View view) {
        // Liste güncelleme işlemleri burada yapılacak
        refreshAppList();
    }

    private void refreshAppList() {
        installedApps = getInstalledApps(); // Yüklü uygulamaları tekrar al
        ArrayAdapter<ApplicationInfo> adapter = (ArrayAdapter<ApplicationInfo>) listView.getAdapter();
        adapter.clear(); // Mevcut listeyi temizle
        adapter.addAll(installedApps); // Yeniden güncellenmiş uygulamaları ekle
        adapter.notifyDataSetChanged(); // Adapter'a değişiklikleri bildir
    }

    public void onDeleteBackupButtonClick(View view) {
        // Yedeği silme işlemi öncesinde onay uyarısı göster
        showDeleteConfirmationDialog();
    }

    private void showDeleteConfirmationDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Confirm Deletion");
        builder.setMessage("Are you sure you want to delete this backup?");
        builder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // Kullanıcı "Evet" dediğinde yedeği sil
                deleteSelectedBackup();
            }
        });
        builder.setNegativeButton("No", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // Kullanıcı "Hayır" dediğinde hiçbir şey yapma, sadece dialogu kapat
                dialog.dismiss();
            }
        });
        builder.show();
    }

    private void deleteSelectedBackup() {
        int selectedPosition = listView.getCheckedItemPosition();
        if (selectedPosition != ListView.INVALID_POSITION) {
            ApplicationInfo selectedAppInfo = installedApps.get(selectedPosition);
            String selectedAppPackageName = selectedAppInfo.packageName;

            File backupDir = new File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), BACKUP_DIR_NAME);
            String backupFileName = selectedAppPackageName + BACKUP_FILE_EXTENSION;
            File backupFile = new File(backupDir, backupFileName);

            if (backupFile.exists()) {
                if (backupFile.delete()) {
                    Toast.makeText(this, "Backup deleted successfully", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Failed to delete backup", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "Backup file not found", Toast.LENGTH_SHORT).show();
            }
        }
    }

    public void onRestoreButtonClick(View view) {
        // Yedeği geri yükleme işlemleri burada yapılacak
        checkStoragePermissionAndRestore();
    }

    private static final int MANAGE_EXTERNAL_STORAGE_REQUEST_CODE = 100;

    private void checkStoragePermissionAndRestore() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.MANAGE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            // İzin verildiyse geri yükleme işlemlerine devam et
            restoreSelectedBackup();
        } else {
            // İzin verilmediyse izin iste
            requestStoragePermission();
        }
    }

    private void requestStoragePermission() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.MANAGE_EXTERNAL_STORAGE)) {
            // Kullanıcıya açıklama göster
            new AlertDialog.Builder(this)
                    .setTitle("İzin Gerekli")
                    .setMessage("Bu izin, harici depoyu yönetmek, geri yükleme işlemleri de dahil olmak üzere gereklidir.")
                    .setPositiveButton("Tamam", (dialog, which) -> ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.MANAGE_EXTERNAL_STORAGE}, MANAGE_EXTERNAL_STORAGE_REQUEST_CODE))
                    .setNegativeButton("İptal", (dialog, which) -> dialog.dismiss())
                    .create()
                    .show();
        } else {
            // Açıklama gerekli değil; izin iste
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.MANAGE_EXTERNAL_STORAGE}, MANAGE_EXTERNAL_STORAGE_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == MANAGE_EXTERNAL_STORAGE_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // İzin verildiyse geri yükleme işlemlerine devam et
                restoreSelectedBackup();
            } else {
                // İzin verilmediyse uygun bir mesaj göster veya gerekli aksiyonu al
                Toast.makeText(this, "Harici depoyu yönetme izni, geri yükleme işlemi için gereklidir", Toast.LENGTH_SHORT).show();
            }
        }
    }
    private void restoreSelectedBackup() {
        int selectedPosition = listView.getCheckedItemPosition();
        if (selectedPosition != ListView.INVALID_POSITION) {
            ApplicationInfo selectedAppInfo = installedApps.get(selectedPosition);
            String selectedAppPackageName = selectedAppInfo.packageName;

            File backupDir = new File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), BACKUP_DIR_NAME);
            String backupFileName = selectedAppPackageName + BACKUP_FILE_EXTENSION;
            File backupFile = new File(backupDir, backupFileName);

            PackageManager packageManager = getPackageManager();
            ApplicationInfo appInfo = null;

            try {
                appInfo = packageManager.getApplicationInfo(selectedAppPackageName, 0);
            } catch (PackageManager.NameNotFoundException e) {
                e.printStackTrace();
            }

            if (appInfo != null) {
                try {
                    restoreApp(appInfo, backupFile);
                    Toast.makeText(this, "Backup restored successfully", Toast.LENGTH_SHORT).show();
                } catch (IOException e) {
                    e.printStackTrace();
                    Toast.makeText(this, "Restore Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "Please choose a non-system application", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void restoreApp(ApplicationInfo appInfo, File backupFile) throws IOException {
        File destinationFile = new File(appInfo.sourceDir);

        if (destinationFile.exists()) {
            try (InputStream inputStream = new FileInputStream(backupFile);
                 OutputStream outputStream = new FileOutputStream(destinationFile)) {

                byte[] buffer = new byte[BUFFER_SIZE];
                int length;
                while ((length = inputStream.read(buffer)) > 0) {
                    outputStream.write(buffer, 0, length);
                }
            } catch (IOException e) {
                e.printStackTrace();
                throw e;
            }
        } else {
            Log.e("RestoreApp", "Application destination file not found");
            throw new IOException("Application destination file not found");
        }
    }

    private boolean isAppDeleted(ApplicationInfo appInfo) {
        PackageManager packageManager = getPackageManager();
        try {
            packageManager.getPackageInfo(appInfo.packageName, 0);
            return false;
        } catch (PackageManager.NameNotFoundException e) {
            return true;
        }
    }

    private void shareApkFile() {
        int selectedPosition = listView.getCheckedItemPosition();
        if (selectedPosition != ListView.INVALID_POSITION) {
            ApplicationInfo selectedAppInfo = installedApps.get(selectedPosition);
            String selectedAppPackageName = selectedAppInfo.packageName;

            File backupDir = new File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), BACKUP_DIR_NAME);

            if (backupDir.exists()) {
                String backupFileName = selectedAppPackageName + BACKUP_FILE_EXTENSION;
                File backupFile = new File(backupDir, backupFileName);

                if (backupFile.exists()) {
                    // Create a Uri for the file
                    Uri apkUri = FileProvider.getUriForFile(
                            MainActivity.this,
                            "com.melihcandemir.apkbackup.fileprovider",
                            backupFile);

                    // Create a sharing intent
                    Intent shareIntent = new Intent(Intent.ACTION_SEND);
                    shareIntent.setType("application/vnd.android.package-archive");
                    shareIntent.putExtra(Intent.EXTRA_STREAM, apkUri);

                    // Grant read permission to the receiving app
                    shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

                    // Start the sharing intent
                    startActivity(Intent.createChooser(shareIntent, "Share APK via"));
                } else {
                    Toast.makeText(this, "Backup file not found", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    private boolean hasBackupFileForPackageName(String packageName) {
        File backupDir = new File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), BACKUP_DIR_NAME);
        if (!backupDir.exists()) {
            return false;
        }

        String backupFileName = packageName + BACKUP_FILE_EXTENSION;
        File backupFile = new File(backupDir, backupFileName);

        return backupFile.exists();
    }
}