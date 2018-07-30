import 'react-native';
import {
    parseInitials,
    truncateText,
    validateEmail
} from '../../../App/Components/Helpers/textHelper';

describe('Test parseInitials helper', () => {

    test('First name is parsed correctly', () => {
        expect(parseInitials('Marc')).toBe('M');
    });

    test('First & last names are parsed correctly', () => {
        expect(parseInitials('Marc Maron')).toBe('MM');
    });

    test('Multi-part names are parsed correctly', () => {
        expect(parseInitials('Marc David Maron')).toBe('MDM');
    });

    test('Multi-part names are parsed correctly', () => {
        expect(parseInitials('Marc David-Maron')).toBe('MDM');
    });

    test('Single digit seasons are parsed correctly', () => {
        expect(parseInitials('Season 2', 'season')).toBe('S2');
    });

    test('Double digit seasons are parsed correctly', () => {
        expect(parseInitials('Season 25', 'season')).toBe('S25');
    });
});

describe('Test truncateText helper', () => {

    test('Short text is truncated correctly', () => {
        expect(truncateText('No one likes the tuna, asshole!', 25)).toBe('No one likes the tuna...');
    });

    test('Long text is truncated correctly', () => {
        expect(truncateText('Ask any racer. Any real racer. It don\'t matter if you win by an inch or a mile. Winning\'s winning', 33)).toBe('Ask any racer. Any real racer...');
    });
});

describe('Test email validation', () => {

    test('Valid email passes', () => {
        expect(validateEmail('gordon@cardiff-electric.com')).toBeTruthy();
    });

    test('Invalid email fails', () => {
        expect(validateEmail('gordon@@cardiff-electric.com')).toBeFalsy();
    });
});