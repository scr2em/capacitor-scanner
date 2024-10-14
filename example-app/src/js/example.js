import { LLScanner } from 'll-scanner';

window.testEcho = () => {
    const inputValue = document.getElementById("echoInput").value;
    LLScanner.echo({ value: inputValue })
}
