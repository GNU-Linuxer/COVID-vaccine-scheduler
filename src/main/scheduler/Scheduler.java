package scheduler;

import scheduler.db.ConnectionManager;
import scheduler.model.Caregiver;
import scheduler.model.Patient;
import scheduler.model.Vaccine;
import scheduler.util.Util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Date;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static java.lang.Integer.parseInt;
import static scheduler.util.Util.strongPasswdChecker;

public class Scheduler {

    // objects to keep track of the currently logged-in user
    // Note: it is always true that at most one of currentCaregiver and currentPatient is not null
    //       since only one user can be logged-in at a time
    private static Caregiver currentCaregiver = null;
    private static Patient currentPatient = null;

    public static void main(String[] args) throws SQLException {
        System.out.println();
        System.out.println("Welcome to the COVID-19 Vaccine Reservation Scheduling Application!");
        // read input from user
        BufferedReader r = new BufferedReader(new InputStreamReader(System.in));
        while (true) {
            // printing greetings text
            printGreetingText();
            System.out.print("> ");
            String response = "";
            try {
                response = r.readLine();
            } catch (IOException e) {
                System.out.println("Please try again!");
            }
            // split the user input by spaces
            String[] tokens = response.split(" ");
            // check if input exists
            if (tokens.length == 0) {
                System.out.println("Please try again!");
                continue;
            }
            // determine which operation to perform
            String operation = tokens[0];
            switch (operation) {
                case "create_patient" -> createPatient(tokens);
                case "create_caregiver" -> createCaregiver(tokens);
                case "login_patient" -> loginPatient(tokens);
                case "login_caregiver" -> loginCaregiver(tokens);
                case "search_caregiver_schedule" -> searchCaregiverSchedule(tokens);
                case "reserve" -> reserve(tokens);
                case "upload_availability" -> uploadAvailability(tokens);
                case "cancel" -> cancel(tokens);
                case "add_doses" -> addDoses(tokens);
                case "show_appointments" -> showAppointments(tokens);
                case "logout" -> logout(tokens);
                case "quit" -> {
                    System.out.println("Bye!");
                    return;
                }
                default -> System.out.println("Invalid operation name!");
            }
        }
    }

    private static void printGreetingText() {
        System.out.println();
        System.out.println("*** Please enter one of the following commands ***");
        System.out.println("> create_patient <username> <password>");
        System.out.println("> create_caregiver <username> <password>");
        System.out.println("> login_patient <username> <password>");
        System.out.println("> login_caregiver <username> <password>");
        System.out.println("> search_caregiver_schedule <date>");
        System.out.println("> reserve <date> <vaccine>");
        System.out.println("> upload_availability <date>");
        System.out.println("> cancel <appointment_id>");
        System.out.println("> add_doses <vaccine> <number>");
        System.out.println("> show_appointments");
        System.out.println("> logout");
        System.out.println("> quit");
        System.out.println();
    }

    private static void createPatient(String[] tokens) {
        createAccount(tokens, false);
    }

    private static void createCaregiver(String[] tokens) {
        createAccount(tokens, true);
    }

    private static void createAccount(String[] tokens, boolean isCaregiver) {
        // create_caregiver/create_patient <username> <password>
        if(tokens == null || tokens.length == 0) {
            System.out.println("Error: No token is provided");
            return;
        }
        // check 1: the length for tokens need to be exactly 3 to include all information (with the operation name)
        if (tokens.length != 3) {
            System.out.println("Incorrect parameters passed in.");
            System.out.println("Usage: " + (isCaregiver ? "create_caregiver" : "create_patient") + " <username> <password>");
            return;
        }

        String username = tokens[1];
        String password = tokens[2];

        // check 2: check if the username has been taken already
        if(usernameExists(username, isCaregiver)) {
            System.out.println("Username taken, try for another username!");
            return;
        }

        // check3: check strong password
        if(!strongPasswdChecker(password)) {
            return;
        }

        byte[] salt = Util.generateSalt();
        byte[] hash = Util.generateHash(password, salt);

        // create the account
        try {
            if(isCaregiver) {
                currentCaregiver = new Caregiver.CaregiverBuilder(username, salt, hash).build();
                // save to caregiver information to our database
                currentCaregiver.saveToDB();
            } else {
                currentPatient = new Patient.PatientBuilder(username, salt, hash).build();
                currentPatient.saveToDB();
            }
            System.out.println(" *** Account created successfully *** ");
        } catch (SQLException e) {
            System.out.println("We've encountered issue when creating a new " + (isCaregiver ? "caregiver" : "patient") + " account. Please try again");
            e.printStackTrace();
        }
    }

    private static boolean usernameExists(String username, boolean isCaregiver) {
        ConnectionManager cm = new ConnectionManager();
        Connection con = cm.createConnection();

        String selectUsername = "SELECT * FROM " + (isCaregiver ? "Caregivers" : "Patients") + " WHERE Username = ?";
        try {
            PreparedStatement statement = con.prepareStatement(selectUsername);
            statement.setString(1, username);
            ResultSet resultSet = statement.executeQuery();
            // returns false if the cursor is not before the first record or if there are no rows in the ResultSet.
            return resultSet.isBeforeFirst();
        } catch (SQLException e) {
            System.out.println("Error occurred when checking username in " + (isCaregiver ? "Caregivers" : "Patients") + " database");
            e.printStackTrace();
        } finally {
            cm.closeConnection();
        }
        return true;
    }

    private static void loginPatient(String[] tokens) {
        login(tokens, false);
    }
    private static void loginCaregiver(String[] tokens) {
        login(tokens, true);
    }

    private static void login(String[] tokens, boolean isCaregiver) {
        // login_caregiver/login_patient <username> <password>
        // check 1: if someone's already logged-in, they need to log out first
        if (currentCaregiver != null || currentPatient != null) {
            System.out.println("Already logged-in!");
            return;
        }
        // check 2: the length for tokens need to be exactly 3 to include all information (with the operation name)
        if (tokens.length != 3) {
            System.out.println("Incorrect number of parameter passed.");
            System.out.println("Usage: " + (isCaregiver ? "login_caregiver" : "login_patient") + " <username> <password>");
            return;
        }

        String username = tokens[1];
        String password = tokens[2];

        Caregiver caregiver = null;
        Patient patient = null;

        try {
            if(isCaregiver) {
                caregiver = new Caregiver.CaregiverGetter(username, password).get();
            } else {
                patient = new Patient.PatientGetter(username, password).get();
            }
        } catch (SQLException e) {
            System.out.println("Error occurred when logging in " + (isCaregiver ? "Caregiver" : "Patient"));
            return;
            // e.printStackTrace();
        }
        // check if the login was successful
        if(isCaregiver) {
            if (caregiver == null) {
                System.out.println("Please try again!");
            } else {
                System.out.println("Caregiver logged in as: " + username);
                currentCaregiver = caregiver;
            }
        } else {
            if (patient == null) {
                System.out.println("Please try again!");
            } else {
                System.out.println("Patient logged in as: " + username);
                currentPatient = patient;
            }
        }
    }

    private static void searchCaregiverSchedule(String[] tokens) {
        // search_caregiver_schedule <date>
        // Both patients and caregivers can perform this operation.
        // Output the username for the caregivers that are available for the date,
        // along with the number of available doses left for each vaccine

        // check 1: whether we've logged in as caregiver or patient
        if (currentCaregiver == null && currentPatient == null) {
            System.out.println("Please login as caregiver or patient first.");
            return;
        }

        if (tokens.length != 2) {
            System.out.println("Incorrect number of parameter passed.");
            System.out.println("Usage: search_caregiver_schedule <date>");
            return;
        }

        ConnectionManager cm = new ConnectionManager();
        Connection con = cm.createConnection();

        String findCaregiverByTime = "SELECT Username FROM Availabilities WHERE Time = ?";
        try {
            PreparedStatement statement = con.prepareStatement(findCaregiverByTime);
            Date d = Date.valueOf(tokens[1]);
            statement.setDate(1, d);
            ResultSet resultSet = statement.executeQuery();
            if(!resultSet.isBeforeFirst()) {
                System.out.println("There are no available caregivers in your requested time: " + tokens[1]);
                return;
            }
            System.out.println("Caregivers available on: " + tokens[1]);
            while (resultSet.next()) {
                System.out.println(resultSet.getString("Username"));
            }
            System.out.println();
        } catch (SQLException e) {
            System.out.println("Error occurred while fetching caregiver scheduling.");
            return;
        } finally {
            cm.closeConnection();
        }

        ConnectionManager cm1 = new ConnectionManager();
        Connection con1 = cm1.createConnection();
        String availableVaccines = "SELECT * FROM Vaccines";
        try {
            PreparedStatement vaccineStatement = con1.prepareStatement(availableVaccines);
            ResultSet vaccineResultSet = vaccineStatement.executeQuery();
            if(!vaccineResultSet.isBeforeFirst()) {
                System.out.println("Sorry, we don't offer COVID-19 vaccines at this location.");
                return;
            }
            System.out.printf("%-20s%s\n", "Brand", "Available doses");
            while (vaccineResultSet.next()) {
                System.out.printf("%-20s%d\n", vaccineResultSet.getString("Name"), vaccineResultSet.getInt("Doses"));
            }
        } catch (SQLException e) {
            System.out.println("Error occurred while fetching vaccine inventory.");
            return;
        } finally {
            cm1.closeConnection();
        }
    }

    private static void reserve(String[] tokens) throws SQLException {
        // reserve <date> <vaccine>
        // Patients perform this operation to reserve an appointment.
        // You will be randomly assigned a caregiver for the reservation on that date.
        // Output the assigned caregiver and the appointment ID for the reservation.

        // check 1: check if the current logged-in user is a patient
        if (currentPatient == null) {
            System.out.println("You must log in as patient to schedule an vaccine appointment");
            return;
        }
        // check 2: the length for tokens need to be exactly 2 to include all information (with the operation name)
        if (tokens.length != 3) {
            System.out.println("Incorrect number of parameter passed.");
            System.out.println("Usage: reserve <date> <vaccine>");
            return;
        }

        // check 3: whether user enter correct date format
        String date = tokens[1];
        try {
            Date.valueOf(date);
        } catch (IllegalArgumentException e) {
            System.out.println("Please enter a valid date!");
            System.out.println("Usage: reserve <date> <vaccine>");
            return;
        }

        // Step 1, find the next empty appointment ID
        int nextAppointmentID = 1;
        String queryMinAppointmentID = "SELECT MAX(AppointmentID) AS MaxAppointmentID FROM Appointments";
        ConnectionManager cm = new ConnectionManager();
        Connection con = cm.createConnection();

        // Note: AppointmentID is a key
        try {
            PreparedStatement statement = con.prepareStatement(queryMinAppointmentID);
            ResultSet resultSet = statement.executeQuery();
            if (resultSet.next()) {
                nextAppointmentID = resultSet.getInt("MaxAppointmentID") + 1;
            }
        } catch (SQLException e) {
            System.out.println("Error occurred while fetching current appointment information.");
            return;
        } finally {
            cm.closeConnection();
        }

        // Step 2, find whether vaccines available
        Vaccine selectedVaccine = new Vaccine.VaccineGetter(tokens[2]).get();
        if(selectedVaccine == null || selectedVaccine.getAvailableDoses() == 0) {
            System.out.println("Sorry, we don't have vaccine " + tokens[2] + " available.");
            return;
        }

        // Step 3, find whether we have caregiver available on that time and remove his/her availability
        // Note: if selected vaccine is not available, we won't reach this step.
        Date selectedDate = Date.valueOf(tokens[1]);
        String selectedCaregiver;
        String queryAvailableCaregiver = "SELECT Username FROM Availabilities WHERE Time = ?";
        ConnectionManager cm3 = new ConnectionManager();
        Connection con3 = cm3.createConnection();

        try {
            PreparedStatement statement1 = con3.prepareStatement(queryAvailableCaregiver);
            statement1.setDate(1, selectedDate);
            ResultSet resultSet = statement1.executeQuery();

            List<String> availableCaregivers = new ArrayList<>();
            if(!resultSet.next()) {
                System.out.println("Sorry, there are no caregiver available at " + tokens[1]);
                return;
            }
            while(resultSet.next()) {
                availableCaregivers.add(resultSet.getString("Username"));
            }
            // Select a random caregiver that are all available on this date
            Random rand = new Random();
            selectedCaregiver =  availableCaregivers.get(rand.nextInt(availableCaregivers.size()));
        } catch (SQLException e) {
            System.out.println("Error occurred while fetching caregiver availability.");
            return;
        } finally {
            cm3.closeConnection();
        }

        // Step 4, make an appointment
        String addAppointment = "INSERT INTO Appointments VALUES (? , ? , ? , ? , ?,  0)";
        ConnectionManager cm4 = new ConnectionManager();
        Connection con4 = cm4.createConnection();

        try {
            PreparedStatement statement = con4.prepareStatement(addAppointment);
            statement.setInt(1, nextAppointmentID);
            statement.setDate(2, selectedDate);
            statement.setString(3, selectedVaccine.getVaccineName());
            statement.setString(4, currentPatient.getUsername());
            statement.setString(5, selectedCaregiver);
            statement.executeUpdate();
        } catch (SQLException e) {
            System.out.println("Error occurred while confirming your COVID-19 vaccine appointment");
            return;
        } finally {
            cm4.closeConnection();
        }

        // Step 5, remove this caregiver's availability
        String removeCaregiverAvailability = "DELETE FROM Availabilities WHERE Time = ? AND Username = ? ";
        ConnectionManager cm5 = new ConnectionManager();
        Connection con5 = cm5.createConnection();
        try {
            PreparedStatement statement = con5.prepareStatement(removeCaregiverAvailability);
            statement.setDate(1, selectedDate);
            statement.setString(2, selectedCaregiver);
            statement.executeUpdate();
        } catch (SQLException e) {
            System.out.println("Your appointment is confirmed, but we could not update your caregiver's new availability.");
            return;
        } finally {
            cm5.closeConnection();
        }

        // Step 6, decrease this vaccine's availability
        selectedVaccine.decreaseAvailableDoses(1);

        System.out.println("Your COVID-19 vaccine on " + tokens[1] + " has been scheduled");
    }

    private static void uploadAvailability(String[] tokens) {
        // upload_availability <date>
        // check 1: check if the current logged-in user is a caregiver
        if (currentCaregiver == null) {
            System.out.println("Please login as a caregiver first!");
            return;
        }
        // check 2: the length for tokens need to be exactly 2 to include all information (with the operation name)
        if (tokens.length != 2) {
            System.out.println("Incorrect number of parameter passed.");
            System.out.println("Usage: upload_availability <date>");
            return;
        }
        String date = tokens[1];
        try {
            Date d = Date.valueOf(date);
            currentCaregiver.uploadAvailability(d);
            System.out.println("Availability uploaded!");
        } catch (IllegalArgumentException e) {
            System.out.println("Please enter a valid date!");
        } catch (SQLException e) {
            System.out.println("Error occurred when uploading availability");
            e.printStackTrace();
        }
    }

    private static void cancel(String[] tokens) throws SQLException {
        // cancel <appointment_id>
        // check 1: check if the current logged-in user is a caregiver or patient
        if (currentCaregiver == null && currentPatient == null) {
            System.out.println("You must log in as caregiver or patient to cancel appointments");
            return;
        }

        // check 2: the length for tokens need to be exactly 1 to include all information (with the operation name)
        if (tokens.length != 2) {
            System.out.println("Incorrect number of parameter passed.");
            System.out.println("cancel <appointment_id>");
            return;
        }
        Date selectedDate;
        String selectedCaregiver;
        String selectedVaccineString;

        // Step 1: Query whether the appointment is present and not marked as cancelled (Cancelled = 1)
        String queryCurrAppointments = "SELECT AppointmentID, Time, CaregiverUsername, VaccineName FROM Appointments WHERE AppointmentID = ? AND Cancelled = 0";

        // Query current appointment
        ConnectionManager cm1 = new ConnectionManager();
        Connection con1 = cm1.createConnection();
        try {
            PreparedStatement queryAppointment = con1.prepareStatement(queryCurrAppointments);
            queryAppointment.setInt(1, Integer.parseInt(tokens[1]));
            ResultSet appointmentResults = queryAppointment.executeQuery();
            if(!appointmentResults.next()) {
                System.out.println("Appointment with ID of " + tokens[1] + " is either not scheduled or already cancelled");
                return;
            }
            selectedCaregiver = appointmentResults.getString("CaregiverUsername");
            selectedVaccineString = appointmentResults.getString("VaccineName");
            selectedDate = appointmentResults.getDate("Time");
        } catch (SQLException e) {
            System.out.println("Error occurred while fetching currently-booked appointments.");
            return;
        } finally {
            cm1.closeConnection();
        }

        // Step 2, increase this vaccine availability by 1
        Vaccine selectedVaccine = new Vaccine.VaccineGetter(selectedVaccineString).get();
        selectedVaccine.increaseAvailableDoses(1);

        // Step 3, add this caregiver back to availability
        String insertCaregiverAvailability = "INSERT INTO Availabilities VALUES (?, ?)";

        // Query current appointment
        ConnectionManager cm2 = new ConnectionManager();
        Connection con2 = cm2.createConnection();
        try {
            PreparedStatement queryAppointment = con2.prepareStatement(insertCaregiverAvailability);
            queryAppointment.setDate(1, selectedDate);
            queryAppointment.setString(2, selectedCaregiver);
            queryAppointment.executeUpdate();
        } catch (SQLException e) {
            System.out.println("Error occurred while updating caregiver's availability after cancellation");
            return;
        } finally {
            cm2.closeConnection();
        }

        // Step 4, set the Cancel to one
        String updateCancellationMark = "UPDATE Appointments SET Cancelled = 1 WHERE AppointmentID = " + Integer.parseInt(tokens[1]);

        // Query current appointment
        ConnectionManager cm4 = new ConnectionManager();
        Connection con4 = cm4.createConnection();
        try {
            PreparedStatement queryAppointment = con4.prepareStatement(updateCancellationMark);
            queryAppointment.executeUpdate();
        } catch (SQLException e) {
            System.out.println("Error occurred while finalizing the appointment cancellation.");
            return;
        } finally {
            cm4.closeConnection();
        }

        System.out.println("Your COVID-19 vaccine with appointment ID of " + tokens[1] + " has been canceled");
    }

    private static void addDoses(String[] tokens) {
        // add_doses <vaccine> <number>
        // check 1: check if the current logged-in user is a caregiver
        if (currentCaregiver == null) {
            System.out.println("Please login as a caregiver first!");
            return;
        }
        // check 2: the length for tokens need to be exactly 3 to include all information (with the operation name)
        if (tokens.length != 3) {
            System.out.println("Please try again!");
            return;
        }
        String vaccineName = tokens[1];
        int doses = parseInt(tokens[2]);
        Vaccine vaccine = null;
        try {
            vaccine = new Vaccine.VaccineGetter(vaccineName).get();
        } catch (SQLException e) {
            System.out.println("Error occurred when adding doses");
            e.printStackTrace();
        }
        // check 3: if getter returns null, it means that we need to create the vaccine and insert it into the Vaccines
        //          table
        if (vaccine == null) {
            try {
                vaccine = new Vaccine.VaccineBuilder(vaccineName, doses).build();
                vaccine.saveToDB();
            } catch (SQLException e) {
                System.out.println("Error occurred when adding doses");
                e.printStackTrace();
            }
        } else {
            // if the vaccine is not null, meaning that the vaccine already exists in our table
            try {
                vaccine.increaseAvailableDoses(doses);
            } catch (SQLException e) {
                System.out.println("Error occurred when adding doses");
                e.printStackTrace();
            }
        }
        System.out.println("Doses updated!");
    }

    private static void showAppointments(String[] tokens) {
        // show_appointments
        //	Output the scheduled appointments for the current user (both patients and caregivers).
        //	For caregivers, you should print the appointment ID, vaccine name, date, and patient name.
        //	For patients, you should print the appointment ID, vaccine name, date, and caregiver name.

        if (tokens.length != 1) {
            System.out.println("Warning: Command show_appointment does not require any argument, ignoring all arguments passed here\n");
        }

        if (currentCaregiver == null && currentPatient == null) {
            System.out.println("You must log in as caregiver or patient to show appointments");
            return;
        }

        final boolean isCaregiver = (currentCaregiver != null);
        String selectedName = "";
        String queryCurrAppointments = "";
        // Print all appointment for current caregiver when logged in as a caregiver
        if (currentCaregiver != null) {
            System.out.println("Showing all patients appointments for " + currentCaregiver.getUsername());
            selectedName = currentCaregiver.getUsername();
            queryCurrAppointments = "SELECT AppointmentID, Time, VaccineName, PatientUsername FROM Appointments WHERE CaregiverUsername = ? AND Cancelled = 0";
        } else if (currentPatient != null) {
            System.out.println("Showing your COVID-19 vaccine appointments");
            selectedName = currentPatient.getUsername();
            queryCurrAppointments = "SELECT AppointmentID, Time, VaccineName, CaregiverUsername FROM Appointments WHERE PatientUsername = ? AND Cancelled = 0";
        }

        // Query appointment
        ConnectionManager cm = new ConnectionManager();
        Connection con = cm.createConnection();
        try {
            PreparedStatement queryAppointment = con.prepareStatement(queryCurrAppointments);
            queryAppointment.setString(1, selectedName);
            ResultSet appointmentResults = queryAppointment.executeQuery();
            boolean isFound = false;
            boolean printHeader = true;
            while (appointmentResults.next()) {
                if(printHeader) {
                    System.out.printf("%-20s%-20s%-20s%s\n", "Appointment ID", "Time", "Vaccine Name", (isCaregiver ? "Patient": "Caregiver"));
                    printHeader = false;
                }
                System.out.printf("%-20d%-20s%-20s%s\n", appointmentResults.getInt("AppointmentID"), appointmentResults.getDate("Time").toString(), appointmentResults.getString("VaccineName"), appointmentResults.getString(isCaregiver?  "PatientUsername": "CaregiverUsername") );
                isFound = true;
            }
            if(!isFound) {
                System.out.println("You don't have any appointment scheduled for " + (isCaregiver ? "Caregiver": "Patient") + " " + selectedName);
            }
        } catch (SQLException e) {
            System.out.println("Error occurred while fetching vaccine inventory.");
            return;
        } finally {
            cm.closeConnection();
        }
    }

    private static void logout(String[] tokens) {
        if (tokens.length != 1) {
            System.out.println("Warning: Command logout does not require any argument, ignoring all arguments passed here\n");
        }

        currentPatient = null;
        currentCaregiver = null;
        System.out.println("Logout successfully");
    }
}
