package dev.elena.deepseek;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class DeepSeekGson {
    public static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
}
