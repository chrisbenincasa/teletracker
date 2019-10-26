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
import React, { Component } from 'react';
import { updateURLParameters } from '../../utils/urlHelper';
import { parseFilterParamsFromQs } from '../../utils/searchFilters';

const styles = (theme: Theme) =>
  createStyles({
    buttonContainer: {
      display: 'flex',
      flexDirection: 'row',
      flexWrap: 'wrap',
    },
    filterButtons: {
      [theme.breakpoints.down('sm')]: {
        fontSize: '0.575rem',
      },
      whiteSpace: 'nowrap',
    },
    typeContainer: { display: 'flex', flexDirection: 'column' },
  });

interface OwnProps {
  handleChange: (type?: ItemType[]) => void;
}

interface RouteParams {
  id: string;
}

type Props = OwnProps &
  WithStyles<typeof styles> &
  RouteComponentProps<RouteParams>;

interface State {
  type?: ItemType[];
}

export const getTypeFromUrlParam = () => {
  return parseFilterParamsFromQs(location.search).itemTypes;
};

class TypeToggle extends Component<Props, State> {
  constructor(props: Props) {
    super(props);

    this.state = {
      ...this.state,
      type: getTypeFromUrlParam(),
    };
  }

  componentDidUpdate = (oldProps: Props) => {
    if (oldProps.location.search !== this.props.location.search) {
      this.setState({
        type: getTypeFromUrlParam(),
      });
    }
  };

  updateURLParam = (param: string, value?: ItemType[]) => {
    updateURLParameters(this.props, param, value);
    this.setState(
      {
        type: value,
      },
      () => {
        this.props.handleChange(value);
      },
    );
  };

  render() {
    const { classes } = this.props;
    const { type } = this.state;

    return (
      <div className={classes.typeContainer}>
        <Typography display="block">By Type:</Typography>
        <div className={classes.buttonContainer}>
          <ButtonGroup
            variant="contained"
            color="primary"
            aria-label="Filter by All, Movies, or just TV Shows"
          >
            <Button
              color={!type ? 'secondary' : 'primary'}
              onClick={() => this.updateURLParam('type', undefined)}
              className={classes.filterButtons}
            >
              All
            </Button>
            <Button
              color={type && type.includes('movie') ? 'secondary' : 'primary'}
              onClick={() => this.updateURLParam('type', ['movie'])}
              className={classes.filterButtons}
            >
              Movies
            </Button>
            <Button
              color={type && type.includes('show') ? 'secondary' : 'primary'}
              onClick={() => this.updateURLParam('type', ['show'])}
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
