package de.codesourcery.logreceiver;

public final class SDParam
{
    public final String id;

    private int paramPtr;
    public String[] paramNames;
    public String[] paramValues;

    public SDParam(String id)
    {
        this.id = id;
    }

    public void addParam(String key,String value)
    {
        if ( paramNames == null || paramPtr == paramNames.length ) {
            int newSize = 1+paramPtr*2;
            paramNames = resize( paramNames == null ? new String[0] : paramNames, newSize );
            paramValues = resize( paramValues == null ? new String[0] : paramValues, newSize );
        }
        paramNames[paramPtr] = key;
        paramValues[paramPtr++] = value;
    }

    public int paramCount() {
        return paramPtr;
    }

    private String[] resize(String[] input, int newSize)
    {
        final String[] result = new String[ newSize ];
        System.arraycopy( input, 0, result, 0 , input.length );
        return result;
    }
}
