package model;

public class Customer extends User {
    private CustomerType customerType;
    private String studentId;

    public Customer() {
    }

    public Customer(String id, String fullName, String phone, String email,
                    CustomerType customerType, String studentId) {
        super(id, fullName, phone, email);
        this.customerType = customerType;
        this.studentId = studentId;
    }

    public CustomerType getCustomerType() {
        return customerType;
    }

    public void setCustomerType(CustomerType customerType) {
        this.customerType = customerType;
    }

    public String getStudentId() {
        return studentId;
    }

    public void setStudentId(String studentId) {
        this.studentId = studentId;
    }
}
