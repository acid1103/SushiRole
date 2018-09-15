package org.abitoff.discord.sushirole;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;

import org.abitoff.discord.sushirole.config.Keys;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

public class SushiRole
{
	public static void main(String[] args) throws JsonParseException,FileNotFoundException
	{
		File keys = new File("./sushiroleconfig/keys.json");
		GsonBuilder builder = new GsonBuilder();
		Gson gson = builder.create();
		Keys k = gson.fromJson(new FileReader(keys), Keys.class);
		System.out.println(k.private_key);
	}
}
