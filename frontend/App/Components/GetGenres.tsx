import React, { Component } from 'react';
import { View } from 'react-native';
import { Badge } from 'react-native-elements';
import getMetadata from './Helpers/getMetadata';
import { Thing } from '../Model/external/themoviedb';

import styles from './Styles/GetGenres';

interface Props {
    item?: Thing
}

export default class GetGenres extends Component<Props> {
    constructor(props: Props) {
        super(props);
    }

    render () {
        return (
            getMetadata.getGenre(this.props.item) ?
                <View style={styles.genreContainer}>
                    {
                        getMetadata.getGenre(this.props.item)
                            ? getMetadata.getGenre(this.props.item).map((i) => (
                                <Badge
                                    key={i.id}
                                    value={i.name}
                                    textStyle={{ color: 'white' }}
                                    wrapperStyle={{
                                        marginHorizontal: 2,
                                        marginVertical: 5
                                    }}
                                />
                            ))
                            : null}
                </View>
            : null
        )
    }
};