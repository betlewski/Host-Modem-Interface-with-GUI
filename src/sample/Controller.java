package sample;

import com.fazecast.jSerialComm.SerialPort;
import com.fazecast.jSerialComm.SerialPortDataListener;
import com.fazecast.jSerialComm.SerialPortEvent;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.io.IOException;
import java.io.InputStream;
import java.util.Vector;

import static com.fazecast.jSerialComm.SerialPort.*;

public class Controller {

    @FXML
    private ComboBox<SerialPort> ports;

    @FXML
    private Button connectButton;

    @FXML
    private Button disconnectButton;

    @FXML
    private Button resetButton;

    @FXML
    private ToggleButton phyButton;

    @FXML
    private ToggleButton dlButton;

    @FXML
    private TextField sendField;

    @FXML
    private RadioButton dataType;

    @FXML
    private Button sendButton;

    @FXML
    private Button clearButton;

    @FXML
    private TextArea hexField;

    @FXML
    private TextArea asciiField;

    @FXML
    private RadioButton BPSK;

    @FXML
    private RadioButton QPSK;

    @FXML
    private RadioButton eightPSK;

    @FXML
    private RadioButton BFSK;

    @FXML
    private RadioButton BPSKcoded;

    @FXML
    private RadioButton QPSKcoded;

    @FXML
    private RadioButton BPSKpna;

    @FXML
    private CheckBox FEC;

    private enum STATE {
        LOOK_4_BEGIN, LOOK_4_LEN, LOOK_4_CC, DATA_COLLECT,
        LOOK_4_FCS_1_BYTE, LOOK_4_FCS_2_BYTE, LOOK_4_STATUS
    }

    private static STATE state = STATE.LOOK_4_BEGIN;
    private static int begin, len, cc, FCS_1, FCS_2, status;
    private static Vector<Integer> data = new Vector<>();

    private enum MOD{
        B_PSK, Q_PSK, eight_PSK, B_FSK,
        B_PSK_coded, Q_PSK_coded, B_PSK_pna
    }

    private static MOD mod = MOD.B_PSK;
    private static boolean fec = false;

    private ToggleGroup toggleGroup = new ToggleGroup();

    private static final byte[] ACK = new byte[]{0x06};
    private static final byte[] NACK = new byte[]{0x15};

    private SerialPort comPort = SerialPort.getCommPorts()[0];

    public void initialize(){

        setButtonsDisable(true);
        setToggleGroup();

        SerialPort[] allPorts = SerialPort.getCommPorts();
        ObservableList<SerialPort> allPortsObservList = FXCollections.observableArrayList(allPorts);
        ports.setItems(allPortsObservList);
    }

    private void setToggleGroup(){
        BPSK.setSelected(true);

        BPSK.setToggleGroup(toggleGroup);
        QPSK.setToggleGroup(toggleGroup);
        eightPSK.setToggleGroup(toggleGroup);
        BFSK.setToggleGroup(toggleGroup);
        BPSKcoded.setToggleGroup(toggleGroup);
        QPSKcoded.setToggleGroup(toggleGroup);
        BPSKpna.setToggleGroup(toggleGroup);
    }

    @FXML
    public void connect() {
        SerialPort chosenPort = ports.getValue();

        if (chosenPort != null) {
            comPort = chosenPort;

            comPort.openPort();
            comPort.setComPortParameters(57600, 8, ONE_STOP_BIT, NO_PARITY);
            comPort.setComPortTimeouts(
                    SerialPort.TIMEOUT_READ_BLOCKING | SerialPort.TIMEOUT_WRITE_BLOCKING, 0, 0);

            comPort.addDataListener(new SerialPortDataListener() {
                @Override
                public int getListeningEvents() {
                    return LISTENING_EVENT_DATA_AVAILABLE; }

                @Override
                public void serialEvent(SerialPortEvent event) {

                    byte[] getByte = new byte[1];
                    InputStream in = comPort.getInputStream();

                    while(comPort.bytesAvailable() > 0) {

                        try {
                            in.read(getByte, 0, 1);

                        } catch (IOException e) {
                            e.printStackTrace();
                        }

                        int hex = getByte[0] & 0xff;

                        switch (state){

                            case LOOK_4_BEGIN:
                                if(hex == 0x02 || hex == 0x03){
                                    begin = hex;
                                    state = STATE.LOOK_4_LEN;
                                }
                                else if(hex == 0x3f){
                                    begin = hex;
                                    state = STATE.LOOK_4_STATUS;
                                }
                                else if(hex == 0x06){
                                    displayFrame("[06]", "[" + (char) 0x06 + "]");
                                }
                                else if(hex == 0x15){
                                    displayFrame("[15]", "[" + (char) 0x15 + "]");
                                }
                                data.clear();

                                break;

                            case LOOK_4_LEN:
                                len = hex;
                                state = STATE.LOOK_4_CC;

                                break;

                            case LOOK_4_CC:
                                cc = hex;
                                state = STATE.DATA_COLLECT;

                                break;

                            case DATA_COLLECT:
                                if(len == 0){
                                    state = STATE.LOOK_4_FCS_1_BYTE;
                                }
                                else {
                                    data.add(hex);
                                    len--;

                                    break;
                                }

                            case LOOK_4_FCS_1_BYTE:
                                FCS_1 = hex;
                                state = STATE.LOOK_4_FCS_2_BYTE;

                                break;

                            case LOOK_4_FCS_2_BYTE:
                                FCS_2 = hex;
                                state = STATE.LOOK_4_BEGIN;

                                Frame localFrame = new Frame(begin, data.size(), cc, data, FCS_1, FCS_2);
                                displayFrame(localFrame.toHexString(), localFrame.toAsciiString());

                                int checkFrame = localFrame.checkFrame();

                                if(checkFrame == 1){
                                    comPort.writeBytes(ACK, 1);
                                }
                                else if(checkFrame == -1){
                                    comPort.writeBytes(NACK, 1);
                                }

                                break;

                            case LOOK_4_STATUS:
                                status = hex;
                                state = STATE.LOOK_4_BEGIN;

                                Frame statusFrame = new Frame(begin, status);
                                displayFrame(statusFrame.toHexString(), statusFrame.toAsciiString());

                                break;
                        }

                        /*String hexToString = String.format("%02x", hex) + " ";
                        javafx.application.Platform.runLater(() -> receiveField.appendText(hexToString));*/
                    }
                }
            });

            setButtonsDisable(false);
        }
    }

    private void displayFrame(String hexFrame, String asciiFrame){

        String hexMessage = hexFrame + "\n";
        javafx.application.Platform.runLater(() -> hexField.appendText(hexMessage));

        String asciiMessage = asciiFrame + "\n";
        javafx.application.Platform.runLater(() -> asciiField.appendText(asciiMessage));
    }

    @FXML
    public void disconnect() {

        comPort.removeDataListener();
        comPort.closePort();

        clear();

        setButtonsDisable(true);
    }

    @FXML
    private void clear(){

        sendField.clear();
        hexField.clear();
        asciiField.clear();
    }

    private void setButtonsDisable(boolean bool){

        ports.setDisable(!bool);

        connectButton.setDisable(!bool);
        disconnectButton.setDisable(bool);
        clearButton.setDisable(bool);
        sendButton.setDisable(bool);
        resetButton.setDisable(bool);

        phyButton.setDisable(bool);
        phyButton.setSelected(false);
        dlButton.setDisable(bool);
        dlButton.setSelected(!bool);

        dataType.setDisable(bool);
        dataType.setSelected(!bool);
    }

    @FXML
    public void setPhy(){

        if(phyButton.isSelected()) {

            dlButton.setSelected(false);

            Vector<Integer> phyData = new Vector<>();
            phyData.add(0x00);
            phyData.add(0x10);

            Frame phyFrame = new Frame(0x02, 0x08, phyData);
            byte[] phyBytes = phyFrame.getBytes();

            beforeWrite();
            comPort.writeBytes(phyBytes, phyBytes.length);
            afterWrite();
        }
        else {
            phyButton.setSelected(true);
        }
    }

    @FXML
    public void setDl(){

        if(dlButton.isSelected()) {

            phyButton.setSelected(false);

            Vector<Integer> dlData = new Vector<>();
            dlData.add(0x00);
            dlData.add(0x11);

            Frame dlFrame = new Frame(0x02, 0x08, dlData);
            byte[] dlBytes = dlFrame.getBytes();

            beforeWrite();
            comPort.writeBytes(dlBytes, dlBytes.length);
            afterWrite();
        }
        else{
            dlButton.setSelected(true);
        }
    }

    @FXML
    public void changeDataType(){

        if(dataType.isSelected()){

            String hexText = sendField.getText();
            String[] bytes = hexText.split("\\s");

            StringBuilder chars = new StringBuilder();

            for (String getByte : bytes) {
                if (getByte.matches("^[a-fA-F0-9]{2}$")) {
                    chars.append((char) Integer.parseInt(getByte, 16));
                }
            }
            sendField.setText(chars.toString());
        }
        else{
            String asciiText = sendField.getText();
            char[] chars = asciiText.toCharArray();

            StringBuilder bytes = new StringBuilder();

            for(char getChar : chars){
                String hex = String.format("%02x", (int) getChar) + " ";
                bytes.append(hex);
            }
            sendField.setText(bytes.toString());
        }
    }

    @FXML
    public void send(){

        beforeWrite();

        //TODO: checking if radio button is clicked
        /* when radio button is clicked:
        String sendText = sendField.getText();
        comPort.writeBytes(sendText.getBytes(), sendText.getBytes().length);*/

        afterWrite();
    }

    @FXML
    public void reset(){

        Frame resetFrame = new Frame(0x00, 0x3c, new Vector<>());
        byte[] resetBytes = resetFrame.getBytes();

        beforeWrite();
        comPort.writeBytes(resetBytes, resetBytes.length);
        afterWrite();

        phyButton.setSelected(false);
        dlButton.setSelected(true);
    }

    private void beforeWrite(){

        comPort.setRTS();
        comPort.setDTR();

        try {
            Thread.sleep(10);

        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    private void afterWrite(){

        comPort.clearRTS();
        comPort.clearDTR();
    }

    @FXML
    public void changeMod(){

        RadioButton radioButton = (RadioButton) toggleGroup.getSelectedToggle();
        String selected = radioButton.getId();

        switch (selected) {

            case "BPSK":
                mod = MOD.B_PSK;
                break;

            case "QPSK":
                mod = MOD.Q_PSK;
                break;

            case "eightPSK":
                mod = MOD.eight_PSK;
                break;

            case "BFSK":
                mod = MOD.B_FSK;
                break;

            case "BPSKcoded":
                mod = MOD.B_PSK_coded;
                break;

            case "QPSKcoded":
                mod = MOD.Q_PSK_coded;
                break;

            case "BPSKpna":
                mod = MOD.B_PSK_pna;
                break;
        }
    }

    @FXML
    public void changeFEC(){

        fec = FEC.isSelected();
    }
}