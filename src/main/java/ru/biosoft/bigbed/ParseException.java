package ru.biosoft.bigbed;

public class ParseException extends RuntimeException {
	public ParseException(String msg)
	{
		super(msg);
	}
	public ParseException(String msg, Throwable cause)
	{
		super(msg, cause);
	}
	public ParseException(Throwable cause)
	{
		super(cause);
	}
}
