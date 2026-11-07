package com.sif.travelduaa;

import android.content.Context;

import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * The duaas the user can pick, and the audio recordings for each one.
 *
 * Recordings come from two places:
 *   1. Built in: every audio file in app/src/main/res/raw/ is found automatically.
 *   2. Added on the device: files the user picks with "Add my own audio".
 *
 * A built-in file is linked to a duaa by its name:
 *   duaa_full.mp3, duaa_full_mishary.mp3  → Travel duaa (full)
 *   duaa_riding.mp3                        → Duaa for getting in the car
 *   duaa_short.mp3                         → Short version
 *   any other name                         → My own recording
 */
public class Duas {

    // =====================================================================
    // Duaas
    // =====================================================================

    public static class Dua {
        public final String id;
        public final String title;
        public final String titleAr;
        public final String source;
        public final String text;
        /** English meaning, so users who don't read Arabic understand the duaa. */
        public final String meaning;

        Dua(String id, String title, String titleAr, String source, String text, String meaning) {
            this.id = id;
            this.title = title;
            this.titleAr = titleAr;
            this.source = source;
            this.text = text;
            this.meaning = meaning;
        }

        public boolean isCustom() { return CUSTOM.equals(id); }
    }

    public static final String CUSTOM = "custom";

    public static final Dua[] ALL = {
            new Dua("full",
                    "Travel duaa (full)",
                    "دعاء السفر كاملاً",
                    "Sahih Muslim 1342",
                    "اللهُ أَكْبَرُ، اللهُ أَكْبَرُ، اللهُ أَكْبَرُ، "
                            + "سُبْحَانَ الَّذِي سَخَّرَ لَنَا هَذَا وَمَا كُنَّا لَهُ مُقْرِنِينَ، "
                            + "وَإِنَّا إِلَى رَبِّنَا لَمُنْقَلِبُونَ، "
                            + "اللَّهُمَّ إِنَّا نَسْأَلُكَ فِي سَفَرِنَا هَذَا الْبِرَّ وَالتَّقْوَى، "
                            + "وَمِنَ الْعَمَلِ مَا تَرْضَى، "
                            + "اللَّهُمَّ هَوِّنْ عَلَيْنَا سَفَرَنَا هَذَا، وَاطْوِ عَنَّا بُعْدَهُ، "
                            + "اللَّهُمَّ أَنْتَ الصَّاحِبُ فِي السَّفَرِ، وَالْخَلِيفَةُ فِي الْأَهْلِ، "
                            + "اللَّهُمَّ إِنِّي أَعُوذُ بِكَ مِنْ وَعْثَاءِ السَّفَرِ، وَكَآبَةِ الْمَنْظَرِ، "
                            + "وَسُوءِ الْمُنْقَلَبِ فِي الْمَالِ وَالْأَهْلِ.",
                    "Allah is the Greatest, Allah is the Greatest, Allah is the Greatest. "
                            + "Glory be to the One who has placed this at our service, "
                            + "for we could never have done it ourselves, "
                            + "and to our Lord we will surely return. "
                            + "O Allah, on this journey of ours we ask You for righteousness and piety, "
                            + "and for deeds that please You. "
                            + "O Allah, make this journey easy for us and shorten its distance for us. "
                            + "O Allah, You are the Companion on the journey "
                            + "and the One who looks after the family we leave behind. "
                            + "O Allah, I seek refuge in You from the hardships of travel, "
                            + "from distressing sights, "
                            + "and from returning to find harm in my wealth or family."),
            new Dua("riding",
                    "Duaa for getting in the car",
                    "دعاء الركوب",
                    "Abu Dawud 2602, Tirmidhi 3446",
                    "بِسْمِ اللهِ، الْحَمْدُ لِلَّهِ، "
                            + "سُبْحَانَ الَّذِي سَخَّرَ لَنَا هَذَا وَمَا كُنَّا لَهُ مُقْرِنِينَ، "
                            + "وَإِنَّا إِلَى رَبِّنَا لَمُنْقَلِبُونَ، "
                            + "الْحَمْدُ لِلَّهِ، الْحَمْدُ لِلَّهِ، الْحَمْدُ لِلَّهِ، "
                            + "اللهُ أَكْبَرُ، اللهُ أَكْبَرُ، اللهُ أَكْبَرُ، "
                            + "سُبْحَانَكَ اللَّهُمَّ إِنِّي ظَلَمْتُ نَفْسِي فَاغْفِرْ لِي، "
                            + "فَإِنَّهُ لَا يَغْفِرُ الذُّنُوبَ إِلَّا أَنْتَ.",
                    "In the name of Allah. All praise is for Allah. "
                            + "Glory be to the One who has placed this at our service, "
                            + "for we could never have done it ourselves, "
                            + "and to our Lord we will surely return. "
                            + "All praise is for Allah, all praise is for Allah, all praise is for Allah. "
                            + "Allah is the Greatest, Allah is the Greatest, Allah is the Greatest. "
                            + "Glory be to You, O Allah. I have wronged myself, so forgive me, "
                            + "for no one forgives sins except You."),
            new Dua("short",
                    "Short version (Quran verses)",
                    "الآيتان من سورة الزخرف",
                    "Surah Az-Zukhruf 43:13-14",
                    "سُبْحَانَ الَّذِي سَخَّرَ لَنَا هَذَا وَمَا كُنَّا لَهُ مُقْرِنِينَ، "
                            + "وَإِنَّا إِلَى رَبِّنَا لَمُنْقَلِبُونَ.",
                    "Glory be to the One who has placed this at our service, "
                            + "for we could never have done it ourselves, "
                            + "and to our Lord we will surely return."),
            new Dua(CUSTOM,
                    "My own recording",
                    "تسجيل من اختياري",
                    "Any audio you choose",
                    "",
                    ""),
    };

    public static Dua byId(String id) {
        if ("other".equals(id)) id = CUSTOM;   // name used by an earlier version
        for (Dua d : ALL) if (d.id.equals(id)) return d;
        return ALL[0];
    }

    /** The duaa whose card is selected. */
    public static Dua selectedDua(Context c) {
        return byId(Prefs.get(c).getString(Prefs.SELECTED_DUA, ALL[0].id));
    }

    public static void selectDua(Context c, Dua d) {
        Prefs.get(c).edit().putString(Prefs.SELECTED_DUA, d.id).apply();
    }

    // =====================================================================
    // Recordings
    // =====================================================================

    public static class Recording {
        /** "raw:<name>" for built in, "file:<name>" for added on the device. */
        public final String key;
        public final String duaId;
        public final String name;      // e.g. "Mishary", "Standard recording", "my_file"
        public final int rawRes;       // built in only, else 0
        public final File file;        // added on the device only, else null

        Recording(String key, String duaId, String name, int rawRes, File file) {
            this.key = key;
            this.duaId = duaId;
            this.name = name;
            this.rawRes = rawRes;
            this.file = file;
        }

        public boolean isBuiltIn() { return rawRes != 0; }

        public String where() { return isBuiltIn() ? "built into the app" : "added by you"; }
    }

    /** Files in res/raw that are notes, not audio. */
    private static final List<String> NOT_AUDIO = Arrays.asList("put_recordings_here");

    /** Every recording the app has, built-in first. */
    public static List<Recording> allRecordings(Context c) {
        List<Recording> list = new ArrayList<>();

        // 1. Built in: read the list of files in res/raw from the app's R class.
        try {
            Class<?> raw = Class.forName(c.getPackageName() + ".R$raw");
            Field[] fields = raw.getFields();
            Arrays.sort(fields, (a, b) -> a.getName().compareTo(b.getName()));
            for (Field f : fields) {
                String file = f.getName();
                if (NOT_AUDIO.contains(file)) continue;
                String duaId = CUSTOM;
                String name = pretty(file);
                for (Dua d : ALL) {
                    if (d.isCustom()) continue;
                    String base = "duaa_" + d.id;
                    if (file.equals(base)) {
                        duaId = d.id;
                        name = "Standard recording";
                    } else if (file.startsWith(base + "_")) {
                        duaId = d.id;
                        name = pretty(file.substring(base.length() + 1));   // duaa_full_mishary → Mishary
                    }
                }
                list.add(new Recording("raw:" + file, duaId, name, f.getInt(null), null));
            }
        } catch (ClassNotFoundException e) {
            // No res/raw folder: no built-in recordings.
        } catch (Exception e) {
            Prefs.log(c, "Could not list built-in recordings: " + e.getMessage());
        }

        // 2. Added on the device: files named rec_<duaId>_<time> in the app's storage.
        migrateOldFiles(c);
        File[] files = c.getFilesDir().listFiles();
        if (files != null) {
            Arrays.sort(files, (a, b) -> a.getName().compareTo(b.getName()));
            for (File f : files) {
                if (!f.getName().startsWith("rec_") || f.length() == 0) continue;
                String[] parts = f.getName().split("_", 3);
                String duaId = parts.length > 1 ? byId(parts[1]).id : CUSTOM;
                String saved = Prefs.get(c).getString("name_" + f.getName(), "");
                list.add(new Recording("file:" + f.getName(), duaId,
                        saved.isEmpty() ? "My recording" : saved, 0, f));
            }
        }
        return list;
    }

    /** The recordings that belong to one duaa. */
    public static List<Recording> recordingsFor(Context c, Dua d) {
        List<Recording> list = new ArrayList<>();
        for (Recording r : allRecordings(c)) if (r.duaId.equals(d.id)) list.add(r);
        return list;
    }

    /** The audio chosen for a duaa, or its first recording, or null if it has none. */
    public static Recording audioFor(Context c, Dua d) {
        List<Recording> list = recordingsFor(c, d);
        if (list.isEmpty()) return null;
        String key = Prefs.get(c).getString(Prefs.AUDIO_FOR + d.id, "");
        for (Recording r : list) if (r.key.equals(key)) return r;
        return list.get(0);
    }

    public static void chooseAudio(Context c, Dua d, Recording r) {
        Prefs.get(c).edit().putString(Prefs.AUDIO_FOR + d.id, r.key).apply();
    }

    /** Volume for one recording, in percent: 10–200, default 100. Above 100 is a boost. */
    public static int volume(Context c, Recording r) {
        return Prefs.get(c).getInt("vol_" + r.key, 100);
    }

    public static void setVolume(Context c, Recording r, int percent) {
        Prefs.get(c).edit().putInt("vol_" + r.key, Math.max(10, Math.min(200, percent))).apply();
    }

    /** What plays when the car starts: the chosen audio of the selected duaa. */
    public static Recording toPlay(Context c) {
        return audioFor(c, selectedDua(c));
    }

    /** Where a new recording added on the device is saved. */
    public static File newUserFile(Context c, Dua d) {
        return new File(c.getFilesDir(), "rec_" + d.id + "_" + System.currentTimeMillis());
    }

    public static void saveUserName(Context c, File f, String name) {
        Prefs.get(c).edit().putString("name_" + f.getName(), name).apply();
    }

    public static void delete(Context c, Recording r) {
        if (r.file == null) return;   // built-in recordings can't be deleted on the device
        //noinspection ResultOfMethodCallIgnored
        r.file.delete();
        Prefs.get(c).edit().remove("name_" + r.file.getName()).remove("vol_" + r.key).apply();
    }

    /** Earlier versions saved recordings as duaa_<id>. Rename them to the current style. */
    private static void migrateOldFiles(Context c) {
        File[] files = c.getFilesDir().listFiles();
        if (files == null) return;
        for (File f : files) {
            if (!f.getName().startsWith("duaa_")) continue;
            //noinspection ResultOfMethodCallIgnored
            f.renameTo(newUserFile(c, byId(f.getName().substring(5))));
        }
    }

    /** my_recording_2 → "My recording 2" */
    private static String pretty(String name) {
        String s = name.startsWith("duaa_") ? name.substring(5) : name;
        s = s.replace('_', ' ').trim();
        if (s.isEmpty()) return name;
        return s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1);
    }
}
