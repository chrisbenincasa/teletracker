import React, { Component } from 'react';
import {
  Button,
  ButtonGroup,
  createStyles,
  Theme,
  Typography,
  withStyles,
  WithStyles,
} from '@material-ui/core';
import { RouteComponentProps, withRouter } from 'react-router-dom';
import { ItemType } from '../../types';
import { parseFilterParamsFromQs } from '../../utils/urlHelper';

const styles = (theme: Theme) =>
  createStyles({
    buttonContainer: {
      display: 'flex',
      flexDirection: 'row',
      flexWrap: 'wrap',
    },
    filterButtons: {
      whiteSpace: 'nowrap',
    },
    filterLabel: {
      paddingBottom: theme.spacing(0.5),
    },
    typeContainer: {
      display: 'flex',
      flexDirection: 'column',
    },
  });

interface OwnProps {
  handleChange: (type?: ItemType[]) => void;
  selectedTypes?: ItemType[];
}

interface RouteParams {
  id: string;
}

type Props = OwnProps &
  WithStyles<typeof styles> &
  RouteComponentProps<RouteParams>;

export const getTypeFromUrlParam = () => {
  return parseFilterParamsFromQs(window.location.search).itemTypes;
};

class TypeToggle extends Component<Props> {
  componentDidUpdate = (oldProps: Props) => {
    if (oldProps.location.search !== this.props.location.search) {
      this.setState({
        type: getTypeFromUrlParam(),
      });
    }
  };

  updateTypes = (param: string, value?: ItemType[]) => {
    console.log(value);
    this.props.handleChange(value);
  };

  render() {
    const { classes, selectedTypes } = this.props;
    const isTypeAll = !selectedTypes || !selectedTypes.length;
    const isTypeMovie = selectedTypes && selectedTypes.includes('movie');
    const isTypeShow = selectedTypes && selectedTypes.includes('show');

    return (
      <div className={classes.typeContainer}>
        <Typography className={classes.filterLabel} display="block">
          Type
        </Typography>
        <div className={classes.buttonContainer}>
          <ButtonGroup
            variant="contained"
            color="primary"
            aria-label="Filter by All, Movies, or just TV Shows"
          >
            <Button
              color={isTypeAll ? 'primary' : 'secondary'}
              onClick={() => this.updateTypes('type', [])}
              className={classes.filterButtons}
            >
              All
            </Button>
            <Button
              color={isTypeMovie ? 'primary' : 'secondary'}
              onClick={() => this.updateTypes('type', ['movie'])}
              className={classes.filterButtons}
            >
              Movies
            </Button>
            <Button
              color={isTypeShow ? 'primary' : 'secondary'}
              onClick={() => this.updateTypes('type', ['show'])}
              className={classes.filterButtons}
            >
              TV
            </Button>
          </ButtonGroup>
        </div>
      </div>
    );
  }
}

export default withStyles(styles)(withRouter(TypeToggle));
