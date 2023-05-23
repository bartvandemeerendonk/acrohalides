package app.Client.Layers.PrivacyLayer;

import app.Client.Datastructures.ByteMap;
import app.Client.Utils.ByteUtils;
import app.Client.Utils.PrivacyUtils;
import app.Client.Layers.ApplicationLayer.Election;
import app.Client.Layers.BlockchainLayer.Vote;

import java.util.*;

public class IdentityStore {
    private final ArrayList<Identity> identities;
    private final HashMap<Election, HashMap<Identity, Boolean>> dilutingIdentities;
    private final HashMap<Election, HashSet<Identity>> identitiesPerElection;
    private final HashMap<Election, ByteMap<Vote>> votesPerKeyPerElection;

    public IdentityStore() {
        identities = new ArrayList<>();
        dilutingIdentities = new HashMap<>();
        identitiesPerElection = new HashMap<>();
        votesPerKeyPerElection = new HashMap<>();
    }

    public void addIdentity(Identity identity) {
        identities.add(identity);
    }

    /**
     * Get the asymmetric keypair at the user's disposal for signature dilution or voting, for a given index.
     * @param index The index of the keypair
     * @return An Identity containing the keypair and information about registration and use of the keys
     */
    public Identity getIdentity(int index) {
        if (index < 0 || index >= identities.size()) {
            return null;
        } else {
            return identities.get(index);
        }
    }

    /**
     * Get the number of asymmetric keypairs at the user's disposal for signature dilution or voting.
     */
    public int getNumberOfIdentities() {
        return identities.size();
    }

    public List<Identity> getIdentities() {
        return identities;
    }

    /**
     * Retrieve the Identity that contains a given public key, if it is owned by this user
     * @param publicKey A byte string representing the public key to search for
     * @return An Identity that contains the public key, or null if this user doesn't have an Identity with the given
     * public key
     */
    public Identity getIdentityForPublicKey(byte[] publicKey) {
        Identity identity = null;
        int i = 0;
        while (identity == null && i < identities.size()) {
            Identity currentIdentity = identities.get(i);
            byte[] identityPublicKey = currentIdentity.getPublicKey();
            if (ByteUtils.byteArraysAreEqual(publicKey, identityPublicKey)) {
                identity = currentIdentity;
            }
            i++;
        }
        return identity;
    }

    public Set<Identity> getIdentitiesForElection(byte[] electionManagerPublicKey, byte[] chainId) {
        Set<Identity> toReturn = null;
        for (Election election: identitiesPerElection.keySet()) {
            if (ByteUtils.byteArraysAreEqual(election.getElectionManagerPublicKey(), electionManagerPublicKey) && ByteUtils.byteArraysAreEqual(election.getId(), chainId)) {
                toReturn = identitiesPerElection.get(election);
            }
        }
        return toReturn;
   }

   public void addIdentityToElection(Election election, Identity identity) {
       if (!identitiesPerElection.containsKey(election)) {
           identitiesPerElection.put(election, new HashSet<>());
       }
       identitiesPerElection.get(election).add(identity);
   }

   public ByteMap<Vote> getVotesPerForElection(Election election) {
       ByteMap<Vote> votesPerKey = votesPerKeyPerElection.get(election);
       if (votesPerKey == null) {
           votesPerKey = new ByteMap<>(PrivacyUtils.PUBLIC_KEY_LENGTH);
           votesPerKeyPerElection.put(election, votesPerKey);
       }
       return votesPerKey;
   }

   public HashMap<Identity, Boolean> getDilutingIdentitiesForElection(Election election) {
       if (dilutingIdentities.get(election) == null) {
           dilutingIdentities.put(election, new HashMap<>());
       }
       return dilutingIdentities.get(election);
   }
}
