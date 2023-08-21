-- Create a function that will set the updated_at column to the current timestamp:
CREATE OR REPLACE FUNCTION update_updated_at_column()
    RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Bind the function to a trigger on the transaction_group table:
CREATE TRIGGER update_transaction_group_modtime
    BEFORE UPDATE ON transaction_group
    FOR EACH ROW
EXECUTE FUNCTION update_updated_at_column();
