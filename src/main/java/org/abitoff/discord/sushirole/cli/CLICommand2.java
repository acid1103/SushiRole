package org.abitoff.discord.sushirole.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.ITypeConverter;
import picocli.CommandLine.Option;

public abstract class CLICommand2
{
	@Command(name = "run",description = "",mixinStandardHelpOptions = true)
	public static final class RunCommand extends CLICommand2
	{
		@Option(names = { "--dev" },description = "",arity = "0..1")
		public boolean dev = false;
		@Option(names = {
				"--shard" },description = "",arity = "2",converter = ShardParameterConverter.class,defaultValue = "0 1")
		public int[] shard = new int[] { 0, 1 };

		public static final class ShardParameterConverter implements ITypeConverter<int[]>
		{
			@Override
			public int[] convert(String value) throws Exception
			{
				System.out.println(value);
				return new int[] { 0, 1 };
			}
		}
	}
}
