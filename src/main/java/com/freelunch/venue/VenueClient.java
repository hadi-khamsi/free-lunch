package com.freelunch.venue;

import com.freelunch.model.Contract;
import java.io.IOException;
import java.util.List;

public interface VenueClient {
    List<Contract> fetchCryptoContracts() throws IOException, InterruptedException;
}