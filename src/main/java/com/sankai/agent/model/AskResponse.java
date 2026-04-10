package com.sankai.agent.model;

import java.util.ArrayList;
import java.util.List;

public class AskResponse {
    private String answer;
    private boolean grounded;
    private List<Citation> citations = new ArrayList<>();

    public AskResponse() {
    }

    public AskResponse(String answer, boolean grounded, List<Citation> citations) {
        this.answer = answer;
        this.grounded = grounded;
        this.citations = citations;
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public boolean isGrounded() {
        return grounded;
    }

    public void setGrounded(boolean grounded) {
        this.grounded = grounded;
    }

    public List<Citation> getCitations() {
        return citations;
    }

    public void setCitations(List<Citation> citations) {
        this.citations = citations;
    }
}

