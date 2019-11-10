import {
  createStyles,
  MenuItem,
  Paper,
  TextField,
  Theme,
  Typography,
  WithStyles,
  withStyles,
} from '@material-ui/core';
import { emphasize } from '@material-ui/core/styles/colorManipulator';
import React from 'react';
import Select from 'react-select';
import { SelectComponents } from 'react-select/lib/components';
import { ValueType } from 'react-select/lib/types';
import { Styles } from 'react-select/lib/styles';
import { CSSProperties } from '@material-ui/core/styles/withStyles';

const styles = (theme: Theme) =>
  createStyles({
    input: {
      display: 'flex',
      padding: 0,
      width: '100%',
    },
    valueContainer: {
      display: 'flex',
      flexWrap: 'wrap',
      flex: 1,
      alignItems: 'center',
      overflow: 'hidden',
    },
    chip: {
      margin: `${theme.spacing(0.5)}px ${theme.spacing(0.25)}px`,
    },
    chipFocused: {
      backgroundColor: emphasize(
        theme.palette.type === 'light'
          ? theme.palette.grey[300]
          : theme.palette.grey[700],
        0.08,
      ),
    },
    noOptionsMessage: {
      padding: `${theme.spacing(1)}px ${theme.spacing(2)}px`,
    },
    singleValue: {
      fontSize: 16,
    },
    placeholder: {
      position: 'absolute',
      left: 2,
      fontSize: 16,
    },
    paper: {
      position: 'absolute',
      zIndex: 1,
      marginTop: theme.spacing(1),
      left: 0,
      right: 0,
    },
    divider: {
      height: theme.spacing(2),
    },
  });

function NoOptionsMessage(props) {
  return (
    <Typography
      color="textSecondary"
      className={props.selectProps.classes.noOptionsMessage}
      {...props.innerProps}
    >
      {props.children}
    </Typography>
  );
}

function inputComponent({ inputRef, ...props }) {
  return <div style={{ width: '100%' }} ref={inputRef} {...props} />;
}

function Control(props) {
  return (
    <TextField
      fullWidth
      InputProps={{
        inputComponent,
        inputProps: {
          className: props.selectProps.classes.input,
          inputRef: props.innerRef,
          children: props.children,
          ...props.innerProps,
        },
      }}
      {...props.selectProps.textFieldProps}
    />
  );
}

function Option(props) {
  return (
    <MenuItem
      buttonRef={props.innerRef}
      selected={props.isFocused}
      component="div"
      style={{
        fontWeight: props.isSelected ? 500 : 400,
      }}
      {...props.innerProps}
    >
      {props.children}
    </MenuItem>
  );
}

function Menu(props) {
  return (
    <Paper
      square
      className={props.selectProps.classes.paper}
      {...props.innerProps}
    >
      {props.children}
    </Paper>
  );
}

function Placeholder(props) {
  return (
    <Typography
      color="textSecondary"
      className={props.selectProps.classes.placeholder}
      {...props.innerProps}
    >
      {props.children}
    </Typography>
  );
}

function SingleValue(props) {
  return (
    <Typography
      className={props.selectProps.classes.singleValue}
      {...props.innerProps}
    >
      {props.children}
    </Typography>
  );
}

function ValueContainer(props) {
  return (
    <div className={props.selectProps.classes.valueContainer}>
      {props.children}
    </div>
  );
}

const components = {
  Control,
  Menu,
  NoOptionsMessage,
  Option,
  Placeholder,
  SingleValue,
  ValueContainer,
};

export type AutocompleteOption<T = string> = {
  value: T;
  label: string;
};

type PlainStyles = { [K in keyof Styles]: CSSProperties };

type OwnProps<T> = {
  options: AutocompleteOption<T>[];
  handleChange: (value: ValueType<AutocompleteOption<T>>) => void;
  placeholder?: string;
  components?: Partial<SelectComponents<AutocompleteOption<T>>>;
  styles?: Partial<PlainStyles>;
};

type Props<T> = OwnProps<T> & WithStyles<typeof styles, true>;

type State<T> = {
  value?: AutocompleteOption<T>;
};

class Autocomplete extends React.Component<Props<any>, State<any>> {
  state: State<any> = {};

  render() {
    let { classes, theme } = this.props;

    const selectStyles = {
      container: base => ({
        ...base,
        width: '100%',
        ...(this.props.styles ? this.props.styles.container || {} : {}),
      }),
      input: base => ({
        ...base,
        color: theme.palette.text.primary,
        '& input': {
          font: 'inherit',
        },
      }),
    };

    return (
      <Select
        classes={classes}
        styles={selectStyles}
        options={this.props.options}
        components={{
          ...components,
          ...(this.props.components || {}),
        }}
        value={null}
        // value={this.state.value}
        onChange={this.props.handleChange}
        placeholder={this.props.placeholder}
        isClearable
      />
    );
  }
}

export default withStyles(styles, { withTheme: true })(Autocomplete);
