package lib.IO;

import java.io.*;

public class IOHandler implements AutoCloseable{
    private final BufferedReader bufferedReader;
    private final BufferedWriter bufferedWriter;
    private String buffer;

    public IOHandler(){
        this.bufferedReader = new BufferedReader(new InputStreamReader(System.in));
        this.bufferedWriter = new BufferedWriter(new OutputStreamWriter(System.out));
    }

    public void readLine() throws IOException {
        this.buffer = this.bufferedReader.readLine();
        if(this.buffer == null || this.buffer.isEmpty()){
            throw new IOException("No input provided.");
        }
    }

    public void write() throws IOException {
        this.bufferedWriter.write(this.validateBuffer(this.buffer));
        this.bufferedWriter.flush();
    }

    public void writeLn() throws IOException {
        this.bufferedWriter.write(this.validateBuffer(this.buffer));
        this.bufferedWriter.newLine();
        this.bufferedWriter.flush();
    }

    public String validateBuffer(String buffer) throws IOException {
        if(buffer == null) throw new IOException("Buffer is empty. Please call readLine() first.");

        return buffer;
    }

    public String getBuffer(){
        return this.buffer;
    }

    @Override
    public void close() {
        try {
            this.bufferedReader.close();
        } catch (IOException e) {
            System.err.println("Failed to close BufferedReader: " + e.getMessage());
        }

        try {
            this.bufferedWriter.close();
        } catch (IOException e) {
            System.err.println("Failed to close BufferedWriter: " + e.getMessage());
        }
    }
}
