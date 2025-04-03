import lib.IO.IOHandler;

import java.io.IOException;

public class Main {
    public static void main(String[] args) {
        try(IOHandler ioHandler  = new IOHandler()){
            ioHandler.readLine();
            ioHandler.write();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}