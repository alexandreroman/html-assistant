function selectPrompt(jquery) {
    $("#prompt").select();
}

let promptBarVisible = true;
function togglePromptBarVisibility() {
    if(promptBarVisible) {
        $("#prompt-bar").fadeTo("fast", 0.2);
    } else {
        $("#prompt-bar").fadeTo("fast", 1);
    }
    promptBarVisible = !promptBarVisible;
}

$(document).ready(selectPrompt);
