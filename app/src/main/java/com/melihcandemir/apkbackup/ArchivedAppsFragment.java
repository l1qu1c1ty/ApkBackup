// ArchivedAppsFragment.Java
package com.melihcandemir.apkbackup;

import androidx.fragment.app.Fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ArchivedAppsFragment extends Fragment {

    private static final String BACKUP_DIR_NAME = "app_backups";
    private static final String BACKUP_FILE_EXTENSION = ".apk";

    private ListView archivedAppsListView;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_archived_apps, container, false);
        archivedAppsListView = view.findViewById(R.id.archivedAppsList);
        refreshArchivedAppsList();
        return view;
    }

    private void refreshArchivedAppsList() {
        List<String> archivedApps = getArchivedAppsList();
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_list_item_1, archivedApps);
        archivedAppsListView.setAdapter(adapter);
    }

    private List<String> getArchivedAppsList() {
        List<String> archivedApps = new ArrayList<>();
        File backupDir = new File(requireContext().getExternalFilesDir(null), BACKUP_DIR_NAME);

        if (backupDir.exists()) {
            for (File file : Objects.requireNonNull(backupDir.listFiles())) {
                if (file.isFile() && file.getName().endsWith(BACKUP_FILE_EXTENSION)) {
                    String appName = file.getName().replace(BACKUP_FILE_EXTENSION, "");
                    archivedApps.add(appName);
                }
            }
        }
        return archivedApps;
    }
}