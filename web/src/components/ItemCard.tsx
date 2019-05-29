import {
  Button,
  Card,
  CardContent,
  CardMedia,
  createStyles,
  Grid,
  Icon,
  Link,
  Theme,
  Typography,
  WithStyles,
  withStyles,
  IconButton,
  Menu,
  MenuItem,
} from '@material-ui/core';
import { Link as RouterLink, withRouter } from 'react-router-dom';
import React, { Component, ReactNode } from 'react';
import Truncate from 'react-truncate';
import AddToListDialog from './AddToListDialog';
import { User, List } from '../types';
import { Thing } from '../types';
import { getDescription, getPosterPath } from '../utils/metadata-access';
import { Dispatch, bindActionCreators } from 'redux';
import { ListUpdate, ListUpdatedInitiatedPayload } from '../actions/lists';
import { connect } from 'react-redux';

const styles = (theme: Theme) =>
  createStyles({
    button: {
      width: '100%',
      marginTop: '0.35em',
    },
    description: {
      marginBottom: '0.35em',
    },
    title: {
      flex: 1,
      whiteSpace: 'nowrap',
      overflow: 'hidden',
      textOverflow: 'ellipsis',
    },
    card: {
      height: '100%',
      display: 'flex',
      flexDirection: 'column',
    },
    cardMedia: {
      height: 0,
      width: '100%',
      paddingTop: '150%',
    },
    cardContent: {
      flexGrow: 1,
    },
    missingMedia: {
      height: '150%',
      color: theme.palette.grey[500],
      display: 'flex',
      backgroundColor: theme.palette.grey[300],
      fontSize: '10em',
    },
    missingMediaIcon: {
      alignSelf: 'center',
      margin: '0 auto',
      display: 'inline-block',
    },
  });

interface ItemCardProps extends WithStyles<typeof styles> {
  key: string | number;
  item: Thing;
  userSelf?: User;

  // display props
  addButton?: boolean;
  itemCardVisible?: boolean;
  // If defined, we're viewing this item within the context of _this_ list
  // This is probably not scalable, but it'll work for now.
  listContext?: List;

  withActionButton: boolean;
}

interface DispatchProps {
  ListUpdate: (payload: ListUpdatedInitiatedPayload) => void;
}

interface ItemCardState {
  modalOpen: boolean;

  // åction button menu
  anchorEl: any;
}

type Props = ItemCardProps & DispatchProps;

class ItemCard extends Component<Props, ItemCardState> {
  static defaultProps = {
    withActionButton: false,
    itemCardVisible: true,
  };

  state: ItemCardState = {
    modalOpen: false,
    anchorEl: null,
  };

  constructor(props: Props) {
    super(props);
    if (
      !props.listContext &&
      props.withActionButton &&
      process.env.NODE_ENV !== 'production'
    ) {
      console.warn('withActionButton=true without listContext will not work.');
    }
  }

  handleModalOpen = (item: Thing) => {
    this.setState({ modalOpen: true });
  };

  handleModalClose = () => {
    this.setState({ modalOpen: false });
  };

  handleActionMenuOpen = ev => {
    this.setState({ anchorEl: ev.currentTarget });
  };

  handleActionMenuClose = () => {
    this.setState({ anchorEl: null });
  };

  handleRemoveFromList = () => {
    this.props.ListUpdate({
      thingId: parseInt(this.props.item.id.toString()),
      addToLists: [],
      removeFromLists: [this.props.listContext!.id.toString()],
    });
  };

  navigateToDetail = (thing: Thing) => {
    // this.props.
  };

  renderPoster = (thing: Thing) => {
    let poster = getPosterPath(thing);

    const makeLink = (children: ReactNode, className?: string) => (
      <Link
        className={className}
        component={props => (
          <RouterLink {...props} to={'/item/' + thing.type + '/' + thing.id} />
        )}
      >
        {children}
      </Link>
    );

    if (poster) {
      return makeLink(
        <CardMedia
          className={this.props.classes.cardMedia}
          image={'https://image.tmdb.org/t/p/w300' + poster}
          title={thing.name}
        />,
      );
    } else {
      return makeLink(
        <div style={{ display: 'flex', width: '100%' }}>
          <Icon
            className={this.props.classes.missingMediaIcon}
            fontSize="inherit"
          >
            broken_image
          </Icon>
        </div>,
        this.props.classes.missingMedia,
      );
    }
  };

  renderActionMenu() {
    let { anchorEl } = this.state;

    return this.props.withActionButton && this.props.listContext ? (
      <React.Fragment>
        <IconButton onClick={this.handleActionMenuOpen}>
          <Icon>more_vert</Icon>
        </IconButton>
        <Menu
          anchorEl={anchorEl}
          open={Boolean(anchorEl)}
          onClose={this.handleActionMenuClose}
          disableAutoFocusItem
        >
          <MenuItem onClick={this.handleRemoveFromList}>Remove</MenuItem>
        </Menu>
      </React.Fragment>
    ) : null;
  }

  render() {
    let { item, classes, addButton, itemCardVisible } = this.props;

    return (
      <React.Fragment>
        <Grid key={item.id} sm={6} md={3} lg={2} item>
          <Card className={classes.card}>
            {this.renderPoster(item)}
            {itemCardVisible && (
              <CardContent className={classes.cardContent}>
                <div
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    margin: '-8px -8px 0 0',
                  }}
                >
                  <Typography
                    className={classes.title}
                    variant="h5"
                    component="h2"
                    title={item.name}
                  >
                    {item.name}
                  </Typography>
                  {this.renderActionMenu()}
                </div>
                <Typography style={{ height: '60px' }}>
                  <Truncate lines={3} ellipsis={<span>...</span>}>
                    {getDescription(item)}
                  </Truncate>
                </Typography>
                {addButton ? (
                  <Button
                    variant="contained"
                    color="primary"
                    className={classes.button}
                    onClick={() => this.handleModalOpen(item)}
                  >
                    <Icon>playlist_add</Icon>
                    <Typography color="inherit">Add to List</Typography>
                  </Button>
                ) : null}
              </CardContent>
            )}
          </Card>
        </Grid>
        {addButton ? (
          <AddToListDialog
            open={this.state.modalOpen}
            userSelf={this.props.userSelf!}
            item={item}
          />
        ) : null}
      </React.Fragment>
    );
  }
}

const mapDispatchToProps = (dispatch: Dispatch) =>
  bindActionCreators(
    {
      ListUpdate,
    },
    dispatch,
  );

export default withStyles(styles)(
  connect(
    null,
    mapDispatchToProps,
  )(ItemCard),
);
