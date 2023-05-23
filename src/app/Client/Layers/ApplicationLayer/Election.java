package app.Client.Layers.ApplicationLayer;

import app.Client.Datastructures.ByteSet;

import java.util.ArrayList;

public class Election {
    public static final int ID_LENGTH = 8;
    public static final int VOTER_ID_LENGTH =  128;
    private byte[] electionManagerPublicKey;
    private byte[] id;
    private ArrayList<Candidate> candidates;
    private ByteSet disenfrachisedVoters;

    public Election(byte[] electionManagerPublicKey, byte[] id) {
        this.electionManagerPublicKey = electionManagerPublicKey;
        this.id = id;
        disenfrachisedVoters = new ByteSet(VOTER_ID_LENGTH);
    }

    public byte[] getElectionManagerPublicKey() {
        return electionManagerPublicKey;
    }

    public byte[] getId() {
        return id;
    }

    public ArrayList<Candidate> getCandidates() {
        return candidates;
    }

    public void setCandidates(ArrayList<Candidate> candidates) {
        this.candidates = candidates;
    }

    public void addDisenfranchisedVoter(byte[] voterId) {
        disenfrachisedVoters.add(voterId);
    }
}
